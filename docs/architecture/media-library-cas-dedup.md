# Media Library — Content-Addressed Storage (CAS) Deduplication

> **Status:** Implemented internal storage architecture
> **Last Updated:** 2026-06-27

## Overview

The media library uses **Content-Addressed Storage (CAS)** to deduplicate identical files within a
workspace. Instead of the legacy reserve-then-upload flow (single `PROCESSING` state), assets now
transition through states that reflect their CAS lifecycle, and identical files within a workspace
share the same physical blob via a `workspace_file_blobs` index.

### Core principle

```text
hash(file) → SHA-256 → (workspace_id, file_hash) → canonical key: assets/{workspaceId}/blobs/{sha256}.{ext}
```

The server **computes the hash during upload** — the client-provided hash is verified, not trusted.
This prevents corrupted or spoofed dedup.

### Why CAS?

- **Storage savings**: Identical files (e.g., same image uploaded to multiple posts) are stored once
- **Tenant isolation**: Dedup is workspace-scoped — files in different workspaces never share blobs
- **Audit trail**: Blob rows are never deleted; they transition through `GARBAGE_COLLECTED` and
  remain for forensic reference

---

## Architecture

### Domain Model

```text
media_assets                         workspace_file_blobs
┌──────────────────────┐             ┌──────────────────────────┐
│ asset_id (PK)        │──┐         │ workspace_id (PK)        │
│ workspace_id         │  │         │ file_hash (PK)           │
│ file_hash            │──┼────────▶│ storage_key              │
│ status               │  │         │ file_size_bytes (nullable until READY)
│ storage_key (nullable│  │         │ detected_media_type (nullable until READY)
│  until READY)        │  │         │ status                   │
│ source_type          │  │         │ failure_reason           │
│ upload_started_at    │  │         │ orphaned_at              │
│ created_at           │  │         │ gc_failure_count         │
│ ...                  │  │         │ created_at               │
└──────────────────────┘  │         │ updated_at               │
                          │         └──────────────────────────┘
                          │         FOREIGN KEY (workspace_id, file_hash)
                          └────────▶ REFERENCES workspace_file_blobs
```

### Status Lifecycle

**Media Asset statuses:**

```text
PENDING_UPLOAD ──▶ UPLOADING ──▶ READY
        │               │
        ▼               ▼
     FAILED ◀──────────────────── (expired after 24h TTL)
        │
        ▼
     DELETED (soft-delete)
```

**Blob statuses:**

```text
UPLOADING ──▶ READY ──▶ READY_FOR_GC ──▶ GARBAGE_COLLECTED
    │                          │
    ▼                          ▼
 FAILED (retryable)       (never deleted from DB)
```

### Key invariants

- `storage_key` on `media_assets` is **NULL until `READY`** — a CHECK constraint enforces
  `storage_key IS NOT NULL` when `status = 'READY'`
- Blob metadata (`detected_media_type`, `file_size_bytes`) is **nullable until the blob reaches
  `READY`** — CHECK constraints enforce non-null on `READY`
- Assets in `DELETED` or `FAILED` status are **not counted** as active references for blob reference
  counting
- Blob rows are **never physically deleted** — they transition to `GARBAGE_COLLECTED` and persist

---

## API Contract

### PUT `/api/workspaces/{workspaceId}/media/assets/{assetId}`

Initiates the upload flow and checks for existing blobs by `(workspaceId, fileHash)`.

**Request body:**

```json
{
  "fileHash": "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2",
  "fileSizeBytes": 1234567,
  "declaredMediaType": "image/jpeg",
  "originalFilename": "banner.jpg"
}
```

**Validations (strict order):**

| Order | Validation                                                                | Failure                     |
|-------|---------------------------------------------------------------------------|-----------------------------|
| 1     | `assetId` is valid UUID v4                                                | 400 Bad Request             |
| 2     | `fileHash` matches `^[a-f0-9]{64}$`                                       | 400 Bad Request             |
| 3     | `fileSizeBytes` between 1 and 500 MB                                      | 400 / 413 Payload Too Large |
| 4     | `declaredMediaType` in `SUPPORTED_MEDIA_TYPES`                            | 400 Bad Request             |
| 5     | `originalFilename` sanitized (no `/`, `\`, `..`, null bytes; ≤ 255 chars) | 400                         |
| 6     | OOXML types require `originalFilename`                                    | 400 Bad Request             |
| 7     | Rate limit ≤ 200 creations/hour per workspace                             | 429 Too Many Requests       |

**Response matrix:**

| Scenario                                    | Status                                        | Body                                                    |
|---------------------------------------------|-----------------------------------------------|---------------------------------------------------------|
| New file (no existing blob)                 | `201 Created`                                 | `{ status: PENDING_UPLOAD, deduped: false, uploadUrl }` |
| Dedup hit (blob READY)                      | `201 Created`                                 | `{ status: READY, deduped: true }`                      |
| Another upload in progress (blob UPLOADING) | `202 Accepted` + `Retry-After: 3`             | `{ status: WAITING_FOR_BLOB }`                          |
| Retry after failed blob                     | `201 Created`                                 | `{ status: PENDING_UPLOAD }`                            |
| Same assetId + same hash                    | `200 OK`                                      | Current asset state                                     |
| Same assetId + different hash               | `409 Conflict`                                | `{ error: ASSET_HASH_MISMATCH }`                        |
| Rate limit exceeded                         | `429 Too Many Requests` + `Retry-After: 3600` | `{ error: RATE_LIMIT_EXCEEDED }`                        |

### POST `/api/workspaces/{workspaceId}/media/assets/{assetId}/upload`

Uploads raw file bytes (`Content-Type: application/octet-stream`).

**Flow:**

1. **Claim upload slot:** `PENDING_UPLOAD/FAILED → UPLOADING`
2. **Stream bytes** to `assets/{workspaceId}/temp/{assetId}.{ext}` while computing SHA-256, counting
   bytes, and validating magic bytes
3. **Verify byte count** → mismatch returns `422 FILE_SIZE_MISMATCH`
4. **Verify hash** → mismatch returns `422 HASH_MISMATCH`
5. **Lock blob `FOR UPDATE`**, then finalize:
    - Blob `READY` (race) → delete temp, mark asset `READY`, `deduped: true`
    - Blob `UPLOADING` → copy temp to canonical key, mark blob+asset `READY`

### DELETE `/api/workspaces/{workspaceId}/media/assets/{assetId}`

Soft-deletes the asset (`status → DELETED`). If no active references remain on the blob, marks it
`READY_FOR_GC` with `orphaned_at = now()`.

---

## Garbage Collection

### `MediaAssetExpirationJob` (runs every 6 hours)

Expires stale `PENDING_UPLOAD` and `UPLOADING` assets (TTL: 24 hours).

- Marks asset `FAILED` with `failure_reason: 'expired:pending_upload_ttl'`
- If blob has zero active references → marks blob `READY_FOR_GC`

### `BlobGarbageCollector` (runs hourly)

Processes `READY_FOR_GC` blobs past 7-day retention:

- Batch size: 100
- Concurrency guard: `FOR UPDATE SKIP LOCKED`
- Max retries: 5 (`gc_failure_count`)
- On success: `UPDATE → GARBAGE_COLLECTED`, delete physical storage object
- On failure: increment `gc_failure_count`, update `last_gc_attempt_at`

---

## Storage Key Format

| Key type      | Pattern                                     |
|---------------|---------------------------------------------|
| Canonical     | `assets/{workspaceId}/blobs/{sha256}.{ext}` |
| Temp (upload) | `assets/{workspaceId}/temp/{assetId}.{ext}` |

The extension in the canonical key is derived from `detected_media_type` (server-detected via magic
bytes), NOT from the client-declared media type.

**Magic byte detection during upload:**

| Format | Magic bytes                                                 |
|--------|-------------------------------------------------------------|
| JPEG   | `FF D8 FF` at offset 0                                      |
| PNG    | `89 50 4E 47` at offset 0                                   |
| GIF    | `47 49 46 38` at offset 0                                   |
| WEBP   | `52 49 46 46` (RIFF) at offset 0, `57 45 42 50` at offset 8 |
| MP4    | `ftyp` box at offset 4                                      |
| OOXML  | ZIP magic (`50 4B 03 04`) + Content-Type cross-check        |

---

## Database Schema

```sql
CREATE TABLE workspace_file_blobs (
    workspace_id          VARCHAR(64)    NOT NULL,
    file_hash             CHAR(64)       NOT NULL,
    storage_key           VARCHAR(255),                          -- nullable until READY
    file_size_bytes       BIGINT,                                -- nullable until READY
    detected_media_type   VARCHAR(64),                           -- nullable until READY
    status                VARCHAR(20)    NOT NULL DEFAULT 'UPLOADING',
    failure_reason        TEXT,
    orphaned_at           TIMESTAMPTZ,
    gc_failure_count      INT            NOT NULL DEFAULT 0,
    last_gc_attempt_at    TIMESTAMPTZ,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (workspace_id, file_hash),

    CONSTRAINT chk_blob_status CHECK (
        status IN ('UPLOADING', 'READY', 'FAILED', 'READY_FOR_GC', 'GARBAGE_COLLECTED')
    ),
    CONSTRAINT chk_blob_storage_when_ready CHECK (
        status != 'READY' OR storage_key IS NOT NULL
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

-- Partial index for GC queries
CREATE INDEX idx_blobs_gc_candidates
    ON workspace_file_blobs (orphaned_at)
    WHERE status = 'READY_FOR_GC';
```

---

## Architecture Decisions

| Decision                | Choice                                                                    | Rationale                                                |
|-------------------------|---------------------------------------------------------------------------|----------------------------------------------------------|
| Dedup scope             | `(workspace_id, file_hash)`                                               | Tenant isolation, avoids cross-workspace coupling        |
| Canonical write timing  | Upload to temp → verify → copy to canonical                               | Prevents poisoning canonical storage with bad uploads    |
| Asset storage key       | Nullable until `READY`; CHECK enforces non-null on `READY`                | Null models "not yet stored" cleanly                     |
| GC model                | Deferred: `READY_FOR_GC` + 7-day retention, UPDATE to `GARBAGE_COLLECTED` | Avoids delete/recreate race; preserves audit trail       |
| Hash authority          | Server computes SHA-256 during upload stream                              | Prevents corrupted or spoofed dedup                      |
| Canonical key extension | Derived from `detected_media_type` server-side                            | Server is authoritative; prevents extension mismatch     |
| Byte-count validation   | Stream counts bytes; rejects if counted ≠ declared                        | Catches truncated/oversized uploads before storage write |

---

## Frontend Integration

- **Hash computation**: `useFileHash` composable — `<100MB` via `crypto.subtle.digest`,
  `>=100MB` via streaming SHA-256
- **PUT polling**: When PUT returns `202 WAITING_FOR_BLOB`, the client polls the PUT endpoint
  after 3 seconds (or the `Retry-After` header value)
- **Upload**: After successful PUT with `PENDING_UPLOAD`, POST raw bytes to the `uploadUrl`
- **Status mapping**: Replace `PROCESSING` references with the new statuses:
  `PENDING_UPLOAD`, `UPLOADING`, `READY`, `FAILED`, `DELETED`

---

## References

- [Architecture Overview](./README.md)
- [ADR: Media Library Storage Configuration](./adr-media-library-storage.md) — bucket-level
  decisions
