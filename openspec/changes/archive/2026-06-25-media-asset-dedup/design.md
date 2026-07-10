# Design: Media Asset Dedup

## Technical Approach

Replace the current reserve-then-upload flow with workspace-scoped content-addressed storage (CAS).
A new `PUT /api/workspaces/{workspaceId}/media/assets/{assetId}` decides dedup vs upload, and
`POST /api/workspaces/{workspaceId}/media/assets/{assetId}/upload` streams bytes to temp storage,
computes SHA-256 server-side, verifies the claimed hash, then finalizes the blob at the canonical
key `assets/{workspaceId}/blobs/{sha256}.{ext}`. `DELETE` soft-deletes the asset and marks the blob
`READY_FOR_GC` when no active references remain.

This follows the existing hexagonal split: new commands/handlers in `application`, new blob port +
R2DBC adapter in `infrastructure/persistence`, and HTTP contracts in `infrastructure/http`. Storage
continues through `StorageApplicationService`; it needs one added `copyObject` operation.

Storage keys:

- **Canonical key**: `canonicalKey(workspaceId, fileHash, detectedMediaType)` — derived from the
  server-detected MIME type after magic-byte validation.
- **Temp key**: `tempKey(workspaceId, assetId, declaredMediaType)` — derived from the
  client-declared
  MIME type for the upload phase.

## Data Model

### `workspace_file_blobs`

`detected_media_type` and `file_size_bytes` are **nullable** — they are only populated when the blob
reaches `READY` status. The row is created at PUT time with null for these fields.

```sql
CREATE TABLE workspace_file_blobs (
    workspace_id          VARCHAR(64)    NOT NULL,
    file_hash            CHAR(64)       NOT NULL,
    storage_key          VARCHAR(255)   NOT NULL,
    file_size_bytes      BIGINT,                              -- nullable until READY
    detected_media_type  VARCHAR(64),                         -- nullable until READY
    status               VARCHAR(20)    NOT NULL DEFAULT 'UPLOADING',
    failure_reason       TEXT,
    orphaned_at          TIMESTAMPTZ,
    gc_failure_count     INT            NOT NULL DEFAULT 0,
    last_gc_attempt_at   TIMESTAMPTZ,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (workspace_id, file_hash),

    CONSTRAINT chk_blob_status CHECK (
        status IN ('UPLOADING', 'READY', 'FAILED', 'READY_FOR_GC', 'GARBAGE_COLLECTED')
    ),
    CONSTRAINT chk_blob_detected_when_ready CHECK (
        status != 'READY' OR detected_media_type IS NOT NULL
    ),
    CONSTRAINT chk_blob_size_when_ready CHECK (
        status != 'READY' OR file_size_bytes IS NOT NULL
    ),
    CONSTRAINT chk_blob_hash_format CHECK (
        file_hash ~ '^[a-f0-9]{64}$'
    )
);
```

### `workspace_file_blobs` Repository Port (excerpt)

```kotlin
interface WorkspaceFileBlobRepository {
    // Insert or upsert blob at PUT time (detectedMediaType and fileSizeBytes are null)
    suspend fun upsertBlob(workspaceId: String, fileHash: String): BlobUpsertResult

    // FOR UPDATE — used by DELETE and expiration before ref-count check
    suspend fun findBlobForUpdate(workspaceId: String, fileHash: String): WorkspaceFileBlob?

    // GC: UPDATE status to GARBAGE_COLLECTED (never DELETE the row)
    suspend fun markAsGarbageCollected(workspaceId: String, fileHash: String)

    // GC: find candidates
    suspend fun findReadyForGC(threshold: Instant, batchSize: Int): Flux<WorkspaceFileBlob>
}
```

## Architecture Decisions

| Decision                 | Choice                                                                                                                              | Alternatives considered         | Rationale                                                   |
|--------------------------|-------------------------------------------------------------------------------------------------------------------------------------|---------------------------------|-------------------------------------------------------------|
| Dedup scope              | CAS key is `(workspace_id, file_hash)`                                                                                              | Global dedup                    | Keeps tenant isolation and avoids cross-workspace coupling  |
| Canonical write timing   | Upload to temp first, copy to canonical after hash verification                                                                     | Write directly to canonical     | Prevents poisoning canonical storage with bad uploads       |
| Asset storage key        | `media_assets.storage_key` nullable until `READY`; DB CHECK enforces non-null on `READY`                                            | Sentinel storage key            | Null models "not yet stored" cleanly                        |
| GC model                 | Deferred GC: `READY_FOR_GC` + `orphaned_at`, UPDATE to `GARBAGE_COLLECTED` + storage delete after 7 days; blob row is never DELETED | Immediate physical row delete   | Avoids delete/recreate race; preserves audit trail          |
| Hash authority           | Server computes SHA-256 during upload stream                                                                                        | Trust client hash               | Prevents corrupted or spoofed dedup                         |
| Canonical key media type | Extension derived from `detectedMediaType` (server-detected via magic bytes)                                                        | Declared media type from client | Server is authoritative; prevents extension mismatch        |
| Byte-count validation    | Stream counts bytes; rejects if counted ≠ declared `fileSizeBytes`                                                                  | Trust Content-Length only       | Catches truncated or oversized uploads before storage write |

## Data Flow

### PUT

1. Validate in strict order: UUID v4, lowercase 64-char hash, size, supported MIME, sanitized
   filename, OOXML filename required, hourly rate limit (`<= 200/hour`).
2. Handler sets `source_type=UPLOADED` internally.
3. Lookup blob by `(workspaceId, fileHash)`:
    - `READY` → create/find asset as `READY`, return `201` with `deduped=true`
    - `UPLOADING` → return `202` + `Retry-After: 3`
    - `FAILED` or `READY_FOR_GC` → normalize blob to `UPLOADING`, clear `orphaned_at`, create asset
      as `PENDING_UPLOAD`
    - missing → insert blob `UPLOADING`, insert asset `PENDING_UPLOAD`
4. Idempotency: same `assetId` + same hash returns current asset; different hash returns `409`
   `ASSET_HASH_MISMATCH`.

### Upload

```
Client -> temp key -> verify hash -> lock blob -> finalize READY
```

1. `claimUploadSlot` transitions asset `PENDING_UPLOAD/FAILED -> UPLOADING` and sets
   `upload_started_at=now()`.
2. Stream raw bytes to `assets/{workspaceId}/temp/{assetId}.{ext}` while computing SHA-256,
   counting bytes, and validating magic bytes. `detected_media_type` and `file_size_bytes` on the
   blob row remain null until finalization — they are only known after upload completes.
3. On byte-count mismatch (counted bytes ≠ declared `fileSizeBytes`): delete temp, mark blob and
   asset
   `FAILED`, return `422 FILE_SIZE_MISMATCH`.
4. On hash mismatch: delete temp, mark blob and asset `FAILED`, return `422 HASH_MISMATCH`.
5. On hash match: `SELECT blob FOR UPDATE`, then branch:
    - blob `READY` → delete temp, mark asset `READY`, `deduped=true`
    - blob `UPLOADING` → copy temp to canonical, delete temp, mark blob `READY`,
      `detected_media_type`, `file_size_bytes`; mark asset `READY`
    - blob `FAILED` / `READY_FOR_GC` during retry → normalize to `UPLOADING`, continue as above

### Expiration

`MediaAssetExpirationJob` runs every 6 hours and expires stale `PENDING_UPLOAD` and `UPLOADING`
assets
(TTL: 24 hours).

1. SELECT assets with `status IN ('PENDING_UPLOAD', 'UPLOADING')` where `created_at` or
   `upload_started_at` (respectively) is older than 24 hours, `LIMIT 100`.
2. For each asset, UPDATE to `FAILED` with `failure_reason='expired:pending_upload_ttl'`.
3. `SELECT blob FOR UPDATE` on `(workspace_id, file_hash)` — prevents race with DELETE or GC.
4. Run `SELECT COUNT(*)` on `media_assets` for same `(workspace_id, file_hash)` where status is not
   `DELETED` or `FAILED`.
5. If count is zero, mark blob `READY_FOR_GC`, set `orphaned_at=now()`.

### Delete and GC

1. `DELETE` marks asset `DELETED` (idempotent if already deleted).
2. `SELECT blob FOR UPDATE` on `(workspace_id, file_hash)` — prevents concurrent GC or expiration
   races.
3. Run `SELECT COUNT(*)` on `media_assets` for same `(workspace_id, file_hash)` where status is not
   `DELETED` or `FAILED`.
4. If count is zero, mark blob `READY_FOR_GC`, set `orphaned_at=now()`.
5. `BlobGarbageCollector` runs hourly, selects `READY_FOR_GC` blobs with:
    - `orphaned_at < now() - 7 days`
    - `gc_failure_count < 5`
    - `LIMIT 100`
    - `FOR UPDATE SKIP LOCKED`
6. GC **UPDATEs blob to `GARBAGE_COLLECTED`** and deletes the storage object at `storage_key`; on
   failure increments `gc_failure_count` and updates `last_gc_attempt_at`. The blob row persists as
   `GARBAGE_COLLECTED` forever (or until manual cleanup).

## File Changes

| File                                                                                     | Action | Description                                                                                                                                                                                     |
|------------------------------------------------------------------------------------------|--------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `server/smp/.../domain/MediaModels.kt`                                                   | Modify | Replace `PROCESSING` lifecycle with asset/blob enums, add CAS fields and nullable `storageKey`                                                                                                  |
| `server/smp/.../application/MediaCommands.kt`                                            | Modify | Add PUT/upload/delete CAS commands and results                                                                                                                                                  |
| `server/smp/.../application/MediaHandlers.kt`                                            | Modify | Add `PutAssetHandler`; refactor upload/delete to CAS behavior and rate-limit check reuse                                                                                                        |
| `server/smp/.../application/MediaRepositories.kt`                                        | Modify | Add `WorkspaceFileBlobRepository`, ref-count, expiration, GC queries, and `markAsGarbageCollected` port                                                                                         |
| `server/smp/.../infrastructure/persistence/R2dbcMediaRepositories.kt`                    | Modify | Map new columns and implement CAS queries                                                                                                                                                       |
| `server/smp/.../infrastructure/http/MediaAssetController.kt` + `MediaDtos.kt`            | Modify | Expose workspace-scoped PUT/upload/delete contracts and 202/422/429 responses                                                                                                                   |
| `server/smp/.../application/StaleAssetReconciler.kt` + `.../MediaReconcilerScheduler.kt` | Modify | Replace stale `PROCESSING` cleanup with `MediaAssetExpirationJob` (handles PENDING_UPLOAD + UPLOADING, 24h TTL) and `BlobGarbageCollector` (UPDATEs blob to GARBAGE_COLLECTED, deletes storage) |
| `server/smp/.../db/changelog/media/002-*.yaml`                                           | Create | Add `workspace_file_blobs`, FK, checks, partial GC index                                                                                                                                        |
| `shared/storage/.../StorageApplicationService.kt` + `.../AbstractS3CompatibleStorage.kt` | Modify | Add temp-to-canonical copy support                                                                                                                                                              |
| `apps/web/app/src/lib/media-api.ts` + `src/stores/media.ts`                              | Modify | Switch to PUT-first upload flow and new statuses                                                                                                                                                |
| `apps/web/app/src/composables/useFileHash.ts`                                            | Create | `<100MB` via `crypto.subtle.digest`, `>=100MB` via streaming SHA-256 abstraction                                                                                                                |

## Interfaces / Contracts

```kotlin
enum class MediaAssetStatus { PENDING_UPLOAD, UPLOADING, READY, FAILED, DELETED }
enum class BlobStatus { UPLOADING, READY, FAILED, READY_FOR_GC, GARBAGE_COLLECTED }
```

PUT request: `fileHash`, `fileSizeBytes`, `declaredMediaType`, `originalFilename?`

PUT response:

- `201 { status: READY, deduped: true }`
- `201 { status: PENDING_UPLOAD, deduped: false, uploadUrl }`
- `202 { status: WAITING_FOR_BLOB, retryAfterSeconds: 3 }`
- `429 { error: RATE_LIMIT_EXCEEDED, retryAfterSeconds: 3600 }`

Upload error responses:

- `422 { error: HASH_MISMATCH }`
- `422 { error: FILE_SIZE_MISMATCH }`

Frontend contract: `useFileHash().computeHash(file)` returns lowercase SHA-256; `media.ts` polls PUT
on `202` before uploading.

## Testing Strategy

| Layer       | What to Test                                                          | Approach                          |
|-------------|-----------------------------------------------------------------------|-----------------------------------|
| Unit        | PUT dedup states, OOXML validation, 429 rate limit                    | Handler tests with mocked repos   |
| Unit        | Upload three-way finalization, hash mismatch, `claimUploadSlot`       | Handler tests with mocked storage |
| Unit        | Delete ref-count and GC scheduling                                    | Handler tests                     |
| Integration | Concurrent same-hash uploads, FK/order invariants, GC query semantics | R2DBC + Postgres tests            |
| Frontend    | Hash composable, PUT polling, dedup hit, upload path                  | Vitest/MSW                        |

## Migration / Rollout

Single Liquibase change: alter `media_assets`, create `workspace_file_blobs`, add FK/checks/indexes.
No backfill, no feature flag, no legacy path.

## Open Questions

- [ ] Choose the concrete streaming SHA-256 implementation for `>=100MB` files.
- [ ] Decide whether magic-byte detection stays inline or moves to a reusable utility.
