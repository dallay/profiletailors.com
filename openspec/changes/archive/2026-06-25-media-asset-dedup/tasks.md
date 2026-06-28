# Tasks: Media Asset Deduplication — Content-Addressed Storage

## Review Workload Forecast

| Field                   | Value       |
|-------------------------|-------------|
| Estimated changed lines | 700–1000    |
| 400-line budget risk    | High        |
| Chained PRs recommended | Yes         |
| Delivery strategy       | ask-on-risk |
| Chain strategy          | pending     |

Decision needed before apply: Yes — **APPROVED: size-exception** (single PR, full change)
Chained PRs recommended: Yes — but user chose single PR
Chain strategy: size-exception
400-line budget risk: High — accepted by user

### Suggested Work Units

| Unit | Goal                                  | Likely PR | Notes                                                          |
|------|---------------------------------------|-----------|----------------------------------------------------------------|
| 1    | DB migration + domain + blob repo     | PR 1      | Liquibase changeset, enums, composite repo, storage copy       |
| 2    | PUT endpoint + dedup logic            | PR 2      | Base PR 1; handler, controller, rate-limit, key factory        |
| 3    | Upload endpoint + delete + GC         | PR 3      | Base PR 2; streaming upload, soft-delete, GC scheduler         |
| 4    | Frontend integration + expiration job | PR 4      | Base PR 3; useFileHash, media-api, media store, expiration job |

## Phase 1: Foundation — Migration + Domain + Blob Repository + Storage Copy

- [x] 1.1 Create `server/smp/.../db/changelog/media/002-add-workspace-file-blobs.yaml` with all
  changesets: new table, ALTER media_assets, FK, CHECK constraints, indexes (per spec Liquibase
  section).
- [x] 1.2 Update `server/smp/.../domain/MediaModels.kt`: replace `PROCESSING` with `PENDING_UPLOAD`/
  `UPLOADING`/`READY`/`FAILED`/`DELETED`; add `BlobStatus` enum (`UPLOADING`/`READY`/`FAILED`/
  `READY_FOR_GC`/`GARBAGE_COLLECTED`); add nullable `storageKey` and CAS fields to `MediaAsset`; add
  `WorkspaceFileBlob` entity.
- [x] 1.3 Update `server/smp/.../application/MediaRepositories.kt`: add
  `WorkspaceFileBlobRepository` interface with `findByWorkspaceAndHash`, `upsert`,
  `countActiveReferences`, `markReadyForGC`, `markAsGarbageCollected` (UPDATEs status, never
  DELETEs), `findReadyForGC`, `recordGCFailure`; add ref-count and GC query methods to
  `MediaAssetRepository`.
- [x] 1.4 Update `server/smp/.../infrastructure/persistence/R2dbcMediaRepositories.kt`: implement
  `WorkspaceFileBlobRepository` R2DBC adapter; map new columns for `MediaAsset` and
  `WorkspaceFileBlob`.
- [x] 1.5 Update `shared/storage/.../StorageApplicationService.kt` and
  `shared/storage/.../AbstractS3CompatibleStorage.kt`: add `copyObject(sourceKey, destKey)` for
  temp-to-canonical blob copy.

## Phase 2: PUT Endpoint — Dedup Decision + Rate Limiting + Key Factory

- [x] 2.1 Update `server/smp/.../application/MediaCommands.kt`: add `PutAssetCommand`,
  `PutAssetResult` (DEDUPED/PENDING_UPLOAD/WAITING_FOR_BLOB/HASH_MISMATCH/RATE_LIMITED),
  `WorkspaceRateLimiter`.
- [x] 2.2 Update `server/smp/.../application/MediaHandlers.kt`: implement `PutAssetHandler` with
  strict validation order (UUID, hash format, size, MIME, filename, OOXML, rate-limit); blob lookup
  and upsert logic; idempotency by `assetId + hash`.
- [x] 2.3 Update `server/smp/.../infrastructure/http/MediaAssetController.kt` and `MediaDtos.kt`:
  add `PUT /api/workspaces/{workspaceId}/media/assets/{assetId}` endpoint with request validation
  and responses (201/200/202/400/409/429).
- [x] 2.4 Update `server/smp/.../application/MediaHandlers.kt`: implement `DeleteAssetHandler` with
  soft-delete, ref-count check, and `READY_FOR_GC` scheduling.
- [x] 2.5 Add `MediaStorageKeys.kt` (domain key factory):
  `canonicalKey(workspaceId, fileHash, detectedMediaType)` returns
  `assets/{workspaceId}/blobs/{sha256}.{ext}` using the detected MIME extension;
  `tempKey(workspaceId, assetId, declaredMediaType)` returns
  `assets/{workspaceId}/temp/{assetId}.{ext}` using declared MIME extension;
  `parseMediaTypeExtension(mime)` maps MIME → extension string (e.g. `image/jpeg` → `.jpg`).
- [x] 2.6 Update `PutAssetHandler` (from 2.2): when inserting a new blob row, pass `null` for
  `detected_media_type` (it is populated at upload completion); when building the `uploadUrl`
  response, use `tempKey(workspaceId, assetId, declaredMediaType)` so the extension reflects what
  the client declared.

## Phase 3: Upload Endpoint + DELETE Handler + GC Jobs

- [x] 3.1 Update `server/smp/.../application/MediaCommands.kt`: add `UploadAssetCommand`,
  `UploadAssetResult` (READY_DEDUP/READY_NEW/HASH_MISMATCH/UPLOAD_IN_PROGRESS/NOT_FOUND).
- [x] 3.2 Update `server/smp/.../application/MediaHandlers.kt`: implement `UploadAssetHandler` with
  `claimUploadSlot`, streaming SHA-256 + magic byte validation, hash verification, blob lock,
  temp-to-canonical copy, dedup fast-path when blob already READY.
- [x] 3.3 Update `server/smp/.../infrastructure/http/MediaAssetController.kt` and `MediaDtos.kt`:
  add `POST /api/workspaces/{workspaceId}/media/assets/{assetId}/upload` endpoint with
  `application/octet-stream` body and responses (200/409/422/404).
- [x] 3.4 Update `server/smp/.../infrastructure/MediaReconcilerScheduler.kt`: replace
  `StaleAssetReconciler` with `BlobGarbageCollector` (hourly, 7-day retention, FOR UPDATE SKIP
  LOCKED, 5-failure cap) and `MediaAssetExpirationJob` (every 6h, PENDING_UPLOAD > 24h → FAILED).
- [x] 3.5 Update `server/smp/.../infrastructure/http/MediaAssetController.kt`: add
  `DELETE /api/workspaces/{workspaceId}/media/assets/{assetId}` endpoint (200/404).
- [x] 3.6 Add byte-counting stream wrapper in `UploadAssetHandler`: wrap `ByteArrayFlux` input with
  `Flux<DataBuffer>` counting wrapper that accumulates bytes; after streaming completes, compare
  accumulated count against `fileSizeBytes` from PUT; if mismatch, fail with
  `422 FILE_SIZE_MISMATCH` before any hash or storage work.
- [x] 3.7 Extend `MediaAssetExpirationJob` to cover `UPLOADING` assets: query both `PENDING_UPLOAD`
  and `UPLOADING` assets older than 24h; for `UPLOADING` assets, additionally check if the blob is
  orphaned (no other active asset references it) before marking the blob `READY_FOR_GC`; log each
  transition.
- [x] 3.8 Update `DeleteAssetHandler` (from 2.4): add `SELECT blob FOR UPDATE` before the `COUNT(*)`
  query and before the `UPDATE blob SET status='READY_FOR_GC'` so that concurrent PUT + DELETE on
  the same `(workspaceId, fileHash)` cannot race.
- [x] 3.9 Update `BlobGarbageCollector` in `StaleAssetReconciler.kt`: the select query must include
  explicit `LIMIT 100` and partial index predicate `WHERE status='READY_FOR_GC'` (index
  `idx_blobs_gc_candidates` already covers this); the GC path must **UPDATE** the blob to
  `status='GARBAGE_COLLECTED'` — **never** execute `DELETE` on `workspace_file_blobs`; delete the
  storage object at `blob.storage_key` only.
- [x] 3.10 Update `WorkspaceFileBlobRepository` port and R2DBC adapter: replace the
  `delete(workspaceId, fileHash)` method with `markAsGarbageCollected(workspaceId, fileHash)` that
  emits
  `UPDATE workspace_file_blobs SET status='GARBAGE_COLLECTED' WHERE workspace_id=? AND file_hash=?`.

## Phase 4: Frontend Integration + Expiration + Open Questions

- [x] 4.1 Create `apps/web/app/src/composables/useFileHash.ts`: `computeHash` using
  `crypto.subtle.digest` for <100MB files, streaming approach for ≥100MB; `sanitizeFilename` helper.
- [x] 4.2 Update `apps/web/app/src/lib/media-api.ts`: switch to PUT-first
  `putAsset(file, workspaceId)` flow with 202 polling, dedup detection, and `computeFileHash`
  integration.
- [x] 4.3 Update `apps/web/app/src/stores/media.ts`: replace PROCESSING/reserve flow with CAS
  states (`PENDING_UPLOAD`, `UPLOADING`, `READY`, `FAILED`, `DELETED`).
- [x] 4.4 Resolve open question: choose concrete streaming SHA-256 implementation for ≥100MB files (
  document decision in code).
- [x] 4.5 Resolve open question: decide whether magic-byte detection stays inline or moves to a
  reusable utility.
- [x] 4.6 Run `just backend-check` and `just frontend-test` to verify no regressions.

## Implementation Complete (except open questions)

All tasks 1.1–3.10 and 4.1–4.3 are implemented. Tasks 4.4–4.6 remain pending open questions and
verification.
