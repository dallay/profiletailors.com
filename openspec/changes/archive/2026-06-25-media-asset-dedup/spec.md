# Delta for Media Library — Content-Addressed Storage Deduplication

> **Status:** Draft for Implementation
> **Change:** media-asset-dedup
> **Revision:** v3.2
> **Date:** 2026-06-24

## MODIFIED Requirements

### Requirement: Media Asset Lifecycle with CAS Deduplication

The system SHALL replace the `PROCESSING` state and reserve flow with a content-addressed storage (CAS)
deduplication model. Assets SHALL transition through states that reflect their CAS lifecycle:
`PENDING_UPLOAD` → `UPLOADING` → `READY` or `FAILED`, and `PENDING_UPLOAD` or `UPLOADING` assets
that exceed the 24-hour upload TTL SHALL transition to `FAILED`. The system SHALL deduplicate
identical files within a workspace by referencing the same `workspace_file_blob`.

The `storage_key` field on `media_assets` SHALL be nullable. It SHALL remain NULL until the asset
reaches `READY` status. A CHECK constraint SHALL enforce `storage_key IS NOT NULL` whenever
`status = 'READY'`.

Assets in `DELETED` status SHALL NOT be counted as active references for blob reference counting.
Assets in `FAILED` status SHALL NOT be counted as active references for blob reference counting.

#### Scenario: PUT with dedup hit (blob already READY)

- GIVEN a blob with hash `sha256_abc123` exists with status `READY` in workspace `ws_abc`
- WHEN a client PUTs a new asset with the same `fileHash` to the same workspace
- THEN the system SHALL return `201 Created` with `status: READY` and `deduped: true`
- AND the new asset SHALL reference the existing blob
- AND no storage upload is required

#### Scenario: PUT with blob UPLOADING (another upload in progress)

- GIVEN a blob with hash `sha256_abc123` exists with status `UPLOADING` in workspace `ws_abc`
- WHEN a client PUTs a new asset with the same `fileHash` to the same workspace
- THEN the system SHALL return `202 Accepted` with `Retry-After: 3`
- AND the response body SHALL contain `status: WAITING_FOR_BLOB`
- AND the client SHALL poll the PUT endpoint after the retry interval until the upload completes or expires

#### Scenario: PUT with blob FAILED (retry upload)

- GIVEN a blob with hash `sha256_abc123` exists with status `FAILED` in workspace `ws_abc`
- WHEN a client PUTs a new asset with the same `fileHash` to the same workspace
- THEN the system SHALL return `201 Created` with `status: PENDING_UPLOAD`
- AND the system SHALL UPDATE the blob status to `UPLOADING`
- AND the client SHALL upload the file content

#### Scenario: UPLOADING asset expires after TTL

- GIVEN a `media_asset` record exists with `status='UPLOADING'` and `upload_started_at < now() - 24 hours`
- WHEN `MediaAssetExpirationJob` runs
- THEN the system SHALL mark the asset `FAILED`
- AND the blob SHALL be reevaluated for `READY_FOR_GC` under the same active-reference rule used by DELETE

#### Scenario: storage_key invariant

- GIVEN a `media_asset` record exists with `status` not equal to `READY`
- WHEN a client queries the asset
- THEN the `storage_key` field SHALL be `null`
- AND a CHECK constraint SHALL prevent any update that sets `storage_key` to a non-null value
  while `status != 'READY'`

#### Scenario: storage_key invariant (READY asset)

- GIVEN a `media_asset` record with `status = 'READY'`
- THEN the `storage_key` field SHALL contain the canonical CAS key
- AND the `storage_key` format SHALL be `assets/{workspaceId}/blobs/{sha256}.{ext}`

### Requirement: Workspace-Scoped Content-Addressed Blob Index

The system SHALL maintain a `workspace_file_blobs` table that serves as the deduplication index.
Each row SHALL be keyed by `(workspace_id, file_hash)` and SHALL represent a single physical blob
stored at the canonical key once the blob reaches `READY`.

Blob state SHALL be one of: `UPLOADING`, `READY`, `FAILED`, `READY_FOR_GC`, `GARBAGE_COLLECTED`.

Blobs in `UPLOADING` state indicate an active upload in progress. Blobs in `READY` state indicate
a verified, usable blob. Blobs in `FAILED` state indicate a failed upload attempt and are retryable.
Blobs in `READY_FOR_GC` state are awaiting garbage collection after the retention period. Blobs in
`GARBAGE_COLLECTED` state indicate that the physical storage object has been deleted while the blob
row remains to satisfy the FK from soft-deleted assets.

Blob metadata discovered only after streaming validation (`storage_key`, `file_size_bytes`,
`detected_media_type`) SHALL remain nullable before `READY`. CHECK constraints SHALL enforce that
`READY` blobs always have non-null canonical metadata.

#### Scenario: Blob referenced by multiple assets

- GIVEN a blob with hash `sha256_abc123` in workspace `ws_abc` is referenced by 3 assets, 2 of which
  have `status = 'READY'` and 1 has `status = 'DELETED'`
- WHEN the system counts active references to the blob
- THEN the count SHALL be `2` (excluding the `DELETED` asset)
- AND the blob SHALL NOT be marked `READY_FOR_GC`

#### Scenario: Blob orphaned when last asset deleted

- GIVEN a blob with hash `sha256_abc123` in workspace `ws_abc` is referenced by exactly 1 active asset
- WHEN that asset is deleted (status → `DELETED`)
- THEN the system SHALL mark the blob as `READY_FOR_GC`
- AND SHALL set `orphaned_at` to the current timestamp

#### Scenario: GC preserves blob row after storage deletion

- GIVEN a blob has `status='READY_FOR_GC'` and is past the 7-day retention period
- WHEN `BlobGarbageCollector` successfully deletes the physical storage object
- THEN the system SHALL UPDATE the blob row to `status='GARBAGE_COLLECTED'`
- AND SHALL NOT DELETE the `workspace_file_blobs` row from the database

---

## ADDED Requirements

### Requirement: PUT Asset Rate Limiting

The system SHALL enforce an hourly creation rate limit of 200 requests per workspace on
`PUT /api/workspaces/{workspaceId}/media/assets/{assetId}`. The rate limit SHALL be evaluated
before any asset or blob creation takes place.

When the limit is exceeded, the system SHALL return `429 Too Many Requests` with:
- HTTP header `Retry-After: 3600`
- Body `{ "error": "RATE_LIMIT_EXCEEDED", "message": "Hourly creation limit exceeded", "retryAfterSeconds": 3600 }`

The rate limit SHALL apply to the PUT creation flow for the authenticated caller in the target
workspace and SHALL be checked before any blob row or media asset row is inserted or updated.
No specific rate-limiting library or algorithm (e.g., sliding window, token bucket) is mandated
by this specification.

#### Scenario: PUT blocked by rate limit

- GIVEN workspace `ws_abc` has received 200 PUT requests in the current hourly window
- WHEN the client sends another PUT request for the same workspace
- THEN the system SHALL return `429 Too Many Requests` with `RATE_LIMIT_EXCEEDED`
- AND the response SHALL include `retryAfterSeconds: 3600`

### Requirement: PUT Asset Dedup Check

The system SHALL expose `PUT /api/workspaces/{workspaceId}/media/assets/{assetId}` as the first step
of the upload flow. This endpoint SHALL check for existing blobs by `(workspaceId, fileHash)` and
determine whether to fast-path (dedup hit) or require upload.

The endpoint SHALL accept the following JSON request body:

```json
{
  "fileHash": "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2",
  "fileSizeBytes": 1234567,
  "declaredMediaType": "image/jpeg",
  "originalFilename": "banner.jpg"
}
```

Server-side validations SHALL be performed in strict order:

| Order | Validation | Failure Response |
|-------|-----------|------------------|
| 1 | `assetId` is valid UUID v4 | 400 Bad Request |
| 2 | `fileHash` matches `^[a-f0-9]{64}$` (lowercase) | 400 Bad Request |
| 3 | `fileSizeBytes` between 1 and MAX_FILE_SIZE (500 MB) | 400 / 413 Payload Too Large |
| 4 | `declaredMediaType` in `SUPPORTED_MEDIA_TYPES` | 400 Bad Request |
| 5 | `originalFilename` sanitized (no `/`, `\`, `..`, null bytes; ≤ 255 chars) | 400 Bad Request |
| 6 | `originalFilename` present for OOXML media types `application/vnd.openxmlformats-officedocument.wordprocessingml.document` and `application/vnd.openxmlformats-officedocument.presentationml.presentation` | 400 Bad Request |
| 7 | Rate limit (creations/hour ≤ 200) checked before asset/blob creation | 429 Too Many Requests |

`originalFilename` SHALL be required for the two OOXML media types listed above because
extension derivation and downstream handling depend on preserving the original OOXML filename
context even though canonical storage uses the CAS key.

The handler SHALL set `source_type` to `'UPLOADED'` internally — this field is NOT sent by the client.

When no blob exists for `(workspaceId, fileHash)`, the handler SHALL INSERT a blob row in
`UPLOADING` with nullable `storage_key`, nullable `file_size_bytes`, and nullable
`detected_media_type` until the POST upload completes.

#### Scenario: PUT new asset (no existing blob)

- GIVEN no blob exists for `(workspaceId, fileHash)`
- WHEN the client sends PUT with the required fields
- THEN the system SHALL INSERT a new blob row with status `UPLOADING`
- AND SHALL INSERT a new `media_asset` row with status `PENDING_UPLOAD`
- AND SHALL return `201 Created` with:
  ```json
  {
    "assetId": "550e8400-e29b-41d4-a716-446655440000",
    "workspaceId": "ws_abc",
    "status": "PENDING_UPLOAD",
    "mediaType": "image/jpeg",
    "deduped": false,
    "uploadUrl": "/api/workspaces/ws_abc/media/assets/550e8400-e29b-41d4-a716-446655440000/upload",
    "createdAt": "2026-06-24T10:00:00Z"
  }
  ```

#### Scenario: PUT idempotent (same assetId + same hash)

- GIVEN an asset with `assetId` exists with `fileHash = sha256_abc123`
- WHEN the client sends PUT with the same `assetId` and `fileHash`
- THEN the system SHALL return `200 OK` with the current asset state
- AND SHALL NOT create a duplicate asset

#### Scenario: PUT hash mismatch (same assetId + different hash)

- GIVEN an asset with `assetId` exists with `fileHash = sha256_abc123`
- WHEN the client sends PUT with the same `assetId` but `fileHash = sha256_def456`
- THEN the system SHALL return `409 Conflict` with:
  ```json
  {
    "error": "ASSET_HASH_MISMATCH",
    "message": "Asset already exists with a different file hash",
    "existingFileHash": "sha256_abc123"
  }
  ```

### Requirement: POST Upload with Streaming Hash Verification

The system SHALL expose `POST /api/workspaces/{workspaceId}/media/assets/{assetId}/upload` for
uploading file content. This endpoint SHALL stream bytes to temporary storage, compute SHA-256
server-side, count the actual bytes received, verify the client-provided hash and declared size,
copy to the canonical CAS key, and mark both the blob and asset as `READY`.

The request body SHALL be the raw file bytes with `Content-Type: application/octet-stream`.

The system SHALL perform the following steps in order:

1. **Claim upload slot:** UPDATE `media_assets` SET `status='UPLOADING', upload_started_at=:now`
   WHERE `asset_id=:id` AND `status IN ('PENDING_UPLOAD','FAILED')`

2. **If claim fails:** SELECT the current asset state:
   - If `status=READY`: Return `200 OK` with `{ status: READY, deduped: true }`
   - If `status=UPLOADING`: Return `409 Conflict` with `UPLOAD_IN_PROGRESS`
   - If `status=DELETED`: Return `404 Not Found`

3. **Stream to temp key:** Upload bytes to `assets/{workspaceId}/temp/{assetId}.{ext}` using the
   extension derived from `declaredMediaType`, while computing the SHA-256 digest, counting
   `actualBytes`, and validating magic bytes concurrently

4. **Detect media type:** Validate magic bytes during streaming:
   - JPEG: `FF D8 FF` at offset 0
   - PNG: `89 50 4E 47` at offset 0
   - GIF: `47 49 46 38` at offset 0
   - WEBP: `52 49 46 46` (RIFF) at offset 0, `57 45 42 50` (WEBP) at offset 8
   - MP4: `ftyp` box at offset 4
   - OOXML: ZIP magic bytes `50 4B 03 04` — cross-check with Content-Type header

5. **Verify actual byte count:** Compare `actualBytes` with the `fileSizeBytes` from the PUT call
   - If mismatch: DELETE temp key, UPDATE blob and asset to `FAILED` with reason `FILE_SIZE_MISMATCH`,
     return `422 Unprocessable Entity`
   - If match: Continue to step 6

6. **Verify hash:** Compare computed SHA-256 with the `fileHash` from the PUT call
   - If mismatch: DELETE temp key, UPDATE blob and asset to `FAILED` with reason `HASH_MISMATCH`,
     return `422 Unprocessable Entity`
   - If match: Continue to step 7

7. **Acquire lock on blob:** SELECT blob FOR UPDATE to prevent race with another upload

8. **If blob already READY** (another concurrent upload won):
   - DELETE temp key
   - UPDATE asset to `READY` with `storage_key=blob.storage_key`, `detected_media_type=blob.detected_media_type`
   - Return `200 OK` with `{ status: READY, deduped: true }`

9. **If blob still UPLOADING** (we are first):
   - COPY temp key to canonical key: `assets/{workspaceId}/blobs/{sha256}.{ext}` using the extension
     derived from `detected_media_type`, not `declaredMediaType`
   - DELETE temp key
   - UPDATE blob to `READY` with `storage_key=canonicalKey`, `file_size_bytes=actualBytes`,
     `detected_media_type`, `orphaned_at=NULL`
   - UPDATE asset to `READY` with `storage_key=canonicalKey`, `detected_media_type`
   - Return `200 OK` with `{ status: READY, deduped: false }`

#### Scenario: Successful upload (no dedup)

- GIVEN an asset with `status=PENDING_UPLOAD` exists for workspace `ws_abc`
- WHEN the client POSTs the file bytes
- THEN the system SHALL compute SHA-256 server-side and count the actual bytes received
- AND SHALL verify both hash and byte count match the PUT declaration
- AND SHALL copy the file to the canonical CAS key derived from `detected_media_type`
- AND SHALL return `200 OK` with `status: READY, deduped: false`

#### Scenario: Upload dedup (another concurrent upload completed first)

- GIVEN an asset with `status=UPLOADING` exists for workspace `ws_abc`
- AND another concurrent request has already marked the blob as `READY`
- WHEN the client POSTs the file bytes
- THEN the system SHALL DELETE the temp file
- AND SHALL UPDATE the asset to `READY` referencing the existing blob metadata
- AND SHALL return `200 OK` with `status: READY, deduped: true`

#### Scenario: Hash mismatch rejection

- GIVEN an asset with `status=PENDING_UPLOAD` exists for workspace `ws_abc`
- WHEN the client POSTs file bytes whose computed SHA-256 does NOT match the claimed `fileHash`
- THEN the system SHALL DELETE the temp file
- AND SHALL UPDATE blob and asset to `FAILED` with `failure_reason='HASH_MISMATCH'`
- AND SHALL return `422 Unprocessable Entity` with:
  ```json
  {
    "error": "HASH_MISMATCH",
    "message": "Server-computed SHA-256 does not match the provided file hash"
  }
  ```

#### Scenario: File size mismatch rejection

- GIVEN an asset with `status=PENDING_UPLOAD` exists for workspace `ws_abc`
- WHEN the client POSTs file bytes whose actual byte count does NOT match the declared `fileSizeBytes`
- THEN the system SHALL DELETE the temp file
- AND SHALL UPDATE blob and asset to `FAILED` with `failure_reason='FILE_SIZE_MISMATCH'`
- AND SHALL return `422 Unprocessable Entity` with:
  ```json
  {
    "error": "FILE_SIZE_MISMATCH",
    "message": "Actual uploaded byte count does not match the declared file size"
  }
  ```

#### Scenario: Declared MIME differs from detected MIME

- GIVEN `declaredMediaType='image/png'` and magic-byte detection returns `image/jpeg`
- WHEN the upload completes successfully
- THEN the temp key SHALL use the `.png` extension during streaming
- AND the canonical CAS key SHALL use the `.jpg` extension after detection

### Requirement: DELETE Asset with Deferred Blob GC

The system SHALL expose `DELETE /api/workspaces/{workspaceId}/media/assets/{assetId}` for soft-deleting
assets. This endpoint SHALL mark the asset as `DELETED` and, if no other active assets reference the
same blob, mark the blob as `READY_FOR_GC` with an `orphaned_at` timestamp.

The system SHALL NOT physically delete the blob in the request path. Physical deletion SHALL be
performed asynchronously by the `BlobGarbageCollector` after a 7-day retention period.

The system SHALL perform the following steps in order:

1. SELECT the asset by `asset_id` and `workspace_id`
2. If not found: Return `404 Not Found`
3. If already `DELETED`: Return `200 OK` idempotently
4. UPDATE asset to `status='DELETED', updated_at=now()`
5. SELECT blob `FOR UPDATE` by `(workspace_id, file_hash)`
6. SELECT COUNT(*) FROM `media_assets` WHERE `workspace_id=?` AND `file_hash=?`
   AND `status NOT IN ('DELETED','FAILED')`
7. If COUNT == 0:
   - UPDATE blob to `status='READY_FOR_GC', orphaned_at=now()`
   - Return `200 OK` with `{ deleted: true, blobScheduledForGC: true }`
8. If COUNT > 0:
   - Return `200 OK` with `{ deleted: true, blobScheduledForGC: false }`

The blob row SHALL remain in `workspace_file_blobs` even after the later GC job deletes the physical
storage object.

#### Scenario: DELETE last asset referencing a blob

- GIVEN workspace `ws_abc` has exactly one active asset with `file_hash=sha256_abc123`
- WHEN that asset is deleted
- THEN the handler SHALL lock the blob row before counting references
- AND the blob `sha256_abc123` SHALL be marked `READY_FOR_GC`
- AND the response SHALL contain `blobScheduledForGC: true`

#### Scenario: DELETE asset with other active references

- GIVEN workspace `ws_abc` has two active assets with `file_hash=sha256_abc123`
- WHEN one asset is deleted
- THEN the blob SHALL remain in `READY` status
- AND the response SHALL contain `blobScheduledForGC: false`

#### Scenario: DELETE idempotent (already deleted)

- GIVEN an asset with `status=DELETED`
- WHEN DELETE is called again
- THEN the system SHALL return `200 OK` with `{ deleted: true, blobScheduledForGC: false }`
- AND SHALL NOT attempt to mark the blob for GC again

### Requirement: Blob Garbage Collector Job

The system SHALL run a scheduled `BlobGarbageCollector` job every hour to physically delete storage
objects for blobs that have been marked `READY_FOR_GC` and have exceeded the 7-day retention period.

The job SHALL:

1. SELECT blobs with `status='READY_FOR_GC'` AND `orphaned_at < now() - 7 days`
   AND `gc_failure_count < 5`
   ORDER BY `orphaned_at ASC` LIMIT 100
   **FOR UPDATE SKIP LOCKED**

2. For each blob:
   a. DELETE the storage object at `blob.storage_key`
   b. If storage delete succeeds: UPDATE the blob row to `status='GARBAGE_COLLECTED'`,
      `last_gc_attempt_at=now()`, `failure_reason=NULL`
   c. If storage delete fails:
      - UPDATE blob with `failure_reason`, `gc_failure_count + 1`, `last_gc_attempt_at`
      - Log the failure and continue to next blob

The job SHALL use `FOR UPDATE SKIP LOCKED` to allow concurrent workers without blocking.

The job SHALL NOT select blobs where `gc_failure_count >= 5` — these require manual intervention.
The job SHALL NEVER DELETE the `workspace_file_blobs` row because soft-deleted assets may still
reference it through the FK.

#### Scenario: GC garbage-collects orphaned blob after retention period

- GIVEN a blob has `status=READY_FOR_GC` and `orphaned_at` is 8 days ago
- WHEN `BlobGarbageCollector` runs
- THEN the storage object SHALL be deleted
- AND the blob row SHALL remain in the database
- AND the blob status SHALL become `GARBAGE_COLLECTED`

#### Scenario: GC skips blob with too many failures

- GIVEN a blob has `status=READY_FOR_GC`, `gc_failure_count=5`, `orphaned_at=8 days ago`
- WHEN `BlobGarbageCollector` runs
- THEN the blob SHALL NOT be selected
- AND an alert SHALL be triggered for manual intervention

#### Scenario: GC handles concurrent workers

- GIVEN `BlobGarbageCollector` is running on instance A and instance B simultaneously
- WHEN both instances query for blobs to delete
- THEN instance B SHALL skip blobs locked by instance A (FOR UPDATE SKIP LOCKED)
- AND no blob SHALL be processed twice

### Requirement: Asset Expiration Job

The system SHALL run a scheduled `MediaAssetExpirationJob` every 6 hours to transition
stale `PENDING_UPLOAD` and `UPLOADING` assets to `FAILED` status.

The job SHALL:

1. SELECT assets with either:
   - `status='PENDING_UPLOAD'` AND `created_at < now() - 24 hours`, or
   - `status='UPLOADING'` AND `upload_started_at < now() - 24 hours`
   ORDER BY `created_at ASC` LIMIT 100

2. For each asset:
   a. UPDATE asset to `status='FAILED'` with reason:
      - `expired:pending_upload_ttl` for stale `PENDING_UPLOAD`, or
      - `expired:uploading_ttl` for stale `UPLOADING`
   b. SELECT blob `FOR UPDATE` by `(workspace_id, file_hash)`
   c. Recompute active references using the same rule as DELETE: count `media_assets` for the same
      `(workspace_id, file_hash)` where `status NOT IN ('DELETED','FAILED')`
   d. If that active-reference count is zero, UPDATE the blob to `status='READY_FOR_GC', orphaned_at=now()`
   e. Log the transition

The lock-and-count sequence SHALL be atomic with respect to concurrent DELETE and PUT retry flows.

#### Scenario: PENDING_UPLOAD asset expires after 24 hours

- GIVEN an asset with `status=PENDING_UPLOAD` and `created_at=25 hours ago`
- WHEN `MediaAssetExpirationJob` runs
- THEN the asset SHALL be marked `FAILED` with `failure_reason='expired:pending_upload_ttl'`

#### Scenario: UPLOADING asset expires after 24 hours

- GIVEN an asset with `status=UPLOADING` and `upload_started_at=25 hours ago`
- WHEN `MediaAssetExpirationJob` runs
- THEN the asset SHALL be marked `FAILED` with `failure_reason='expired:uploading_ttl'`
- AND the blob SHALL be checked under lock and marked `READY_FOR_GC` only if it has no active references

### Requirement: MIME to Extension Mapping

The system SHALL derive temporary-key extensions from declared media types and canonical-key
extensions from detected media types.

| Media Type | Extension | Magic Bytes |
|-----------|-----------|-------------|
| `image/jpeg` | `.jpg` | `FF D8 FF` |
| `image/png` | `.png` | `89 50 4E 47` |
| `image/gif` | `.gif` | `47 49 46 38` |
| `image/webp` | `.webp` | `52 49 46 46` + `57 45 42 50` |
| `video/mp4` | `.mp4` | `ftyp` at offset 4 |
| `application/pdf` | `.pdf` | `25 50 44 46` |
| `application/msword` | `.doc` | DDE (text), OLE2 (binary) |
| `application/vnd.openxmlformats-officedocument.wordprocessingml.document` | `.docx` | ZIP magic `50 4B 03 04` |
| `application/vnd.ms-powerpoint` | `.ppt` | OLE2 magic |
| `application/vnd.openxmlformats-officedocument.presentationml.presentation` | `.pptx` | ZIP magic `50 4B 03 04` |

#### Scenario: Canonical storage key generation

- GIVEN workspace `ws_abc`, file hash `sha256_def456`, and detected media type `image/jpeg`
- THEN the canonical storage key SHALL be `assets/ws_abc/blobs/sha256_def456.jpg`

#### Scenario: Temp storage key generation

- GIVEN workspace `ws_abc`, asset ID `550e8400-e29b-41d4-a716-446655440000`, and declared media type `image/png`
- THEN the temp storage key SHALL be `assets/ws_abc/temp/550e8400-e29b-41d4-a716-446655440000.png`

#### Scenario: Canonical key uses detected MIME, not declared MIME

- GIVEN declared media type `image/png` and detected media type `image/jpeg`
- WHEN the upload is finalized
- THEN the canonical key SHALL be `assets/{workspaceId}/blobs/{sha256}.jpg`
- AND it SHALL NOT use the `.png` extension

---

## Data Model

### `workspace_file_blobs` (New Table)

```sql
CREATE TABLE workspace_file_blobs (
    workspace_id          VARCHAR(64)    NOT NULL,
    file_hash             CHAR(64)       NOT NULL,
    storage_key           VARCHAR(255),
    file_size_bytes       BIGINT,
    detected_media_type   VARCHAR(64),
    status                VARCHAR(20)    NOT NULL DEFAULT 'UPLOADING',
    failure_reason        TEXT,
    orphaned_at           TIMESTAMPTZ,
    gc_failure_count      INT            NOT NULL DEFAULT 0,
    last_gc_attempt_at    TIMESTAMPTZ,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_workspace_file_blobs PRIMARY KEY (workspace_id, file_hash),
    CONSTRAINT fk_workspace_file_blobs_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces(id),

    CONSTRAINT chk_blob_status CHECK (
        status IN ('UPLOADING', 'READY', 'FAILED', 'READY_FOR_GC', 'GARBAGE_COLLECTED')
    ),
    CONSTRAINT chk_blob_hash_format CHECK (
        file_hash ~ '^[a-f0-9]{64}$'
    ),
    CONSTRAINT chk_blob_storage_when_ready CHECK (
        status != 'READY' OR storage_key IS NOT NULL
    ),
    CONSTRAINT chk_blob_media_type_when_ready CHECK (
        status != 'READY' OR detected_media_type IS NOT NULL
    ),
    CONSTRAINT chk_blob_size_when_ready CHECK (
        status != 'READY' OR file_size_bytes IS NOT NULL
    )
);

CREATE INDEX idx_blobs_status ON workspace_file_blobs(workspace_id, status);
CREATE INDEX idx_blobs_gc_candidates
    ON workspace_file_blobs(status, orphaned_at)
    WHERE status = 'READY_FOR_GC';
```

### `media_assets` (ALTER TABLE)

```sql
ALTER TABLE media_assets
  ADD COLUMN file_hash            CHAR(64)       NOT NULL,
  ADD COLUMN detected_media_type  VARCHAR(64),
  ADD COLUMN failure_reason       TEXT,
  ADD COLUMN upload_started_at    TIMESTAMPTZ,
  ADD COLUMN updated_at           TIMESTAMPTZ;

ALTER TABLE media_assets
  ALTER COLUMN storage_key DROP NOT NULL;

ALTER TABLE media_assets
  ADD CONSTRAINT chk_asset_status CHECK (
      status IN ('PENDING_UPLOAD', 'UPLOADING', 'READY', 'FAILED', 'DELETED')
  ),
  ADD CONSTRAINT chk_asset_hash_format CHECK (
      file_hash ~ '^[a-f0-9]{64}$'
  ),
  ADD CONSTRAINT chk_asset_storage_when_ready CHECK (
      status != 'READY' OR storage_key IS NOT NULL
  );

ALTER TABLE media_assets
  ADD CONSTRAINT fk_media_asset_blob
  FOREIGN KEY (workspace_id, file_hash)
  REFERENCES workspace_file_blobs(workspace_id, file_hash);
```

---

## API Response Schemas

### PUT /api/workspaces/{workspaceId}/media/assets/{assetId}

**Response: 201 Created (new asset)**
```json
{
  "assetId": "550e8400-e29b-41d4-a716-446655440000",
  "workspaceId": "ws_abc",
  "status": "PENDING_UPLOAD",
  "mediaType": "image/jpeg",
  "deduped": false,
  "uploadUrl": "/api/workspaces/ws_abc/media/assets/550e8400-e29b-41d4-a716-446655440000/upload",
  "createdAt": "2026-06-24T10:00:00Z"
}
```

**Response: 200 OK (idempotent PUT)**
```json
{
  "assetId": "550e8400-e29b-41d4-a716-446655440000",
  "workspaceId": "ws_abc",
  "status": "READY",
  "mediaType": "image/jpeg",
  "deduped": true,
  "createdAt": "2026-06-24T10:00:00Z"
}
```

**Response: 202 Accepted (blob UPLOADING)**
```
HTTP/1.1 202 Accepted
Retry-After: 3
```
```json
{
  "status": "WAITING_FOR_BLOB",
  "message": "Another upload for this file hash is in progress",
  "retryAfterSeconds": 3
}
```

**Response: 409 Conflict (hash mismatch)**
```json
{
  "error": "ASSET_HASH_MISMATCH",
  "message": "Asset already exists with a different file hash",
  "existingFileHash": "0f3a..."
}
```

**Response: 400 Bad Request (validation failure)**
```json
{
  "error": "VALIDATION_ERROR",
  "message": "Invalid file hash format",
  "details": { "field": "fileHash" }
}
```

**Response: 429 Too Many Requests**
```json
{
  "error": "RATE_LIMIT_EXCEEDED",
  "message": "Hourly creation limit exceeded",
  "retryAfterSeconds": 3600
}
```

### POST /api/workspaces/{workspaceId}/media/assets/{assetId}/upload

**Request:** Raw bytes, `Content-Type: application/octet-stream`

**Response: 200 OK (upload success)**
```json
{
  "assetId": "550e8400-e29b-41d4-a716-446655440000",
  "workspaceId": "ws_abc",
  "status": "READY",
  "mediaType": "image/jpeg",
  "detectedMediaType": "image/jpeg",
  "deduped": false,
  "fileSizeBytes": 1234567,
  "createdAt": "2026-06-24T10:00:00Z"
}
```

**Response: 200 OK (dedup hit during upload)**
```json
{
  "assetId": "550e8400-e29b-41d4-a716-446655440000",
  "workspaceId": "ws_abc",
  "status": "READY",
  "mediaType": "image/jpeg",
  "detectedMediaType": "image/jpeg",
  "deduped": true,
  "fileSizeBytes": 1234567,
  "createdAt": "2026-06-24T10:00:00Z"
}
```

**Response: 409 Conflict (upload in progress)**
```json
{
  "error": "UPLOAD_IN_PROGRESS",
  "message": "An upload is already in progress for this asset"
}
```

**Response: 422 Unprocessable Entity (hash mismatch)**
```json
{
  "error": "HASH_MISMATCH",
  "message": "Server-computed SHA-256 does not match the provided file hash"
}
```

**Response: 422 Unprocessable Entity (file size mismatch)**
```json
{
  "error": "FILE_SIZE_MISMATCH",
  "message": "Actual uploaded byte count does not match the declared file size"
}
```

**Response: 404 Not Found (asset deleted)**
```json
{
  "error": "ASSET_NOT_FOUND",
  "message": "Asset not found or has been deleted"
}
```

### DELETE /api/workspaces/{workspaceId}/media/assets/{assetId}

**Response: 200 OK (deleted, blob scheduled for GC)**
```json
{
  "deleted": true,
  "blobScheduledForGC": true
}
```

**Response: 200 OK (deleted, blob still referenced)**
```json
{
  "deleted": true,
  "blobScheduledForGC": false
}
```

**Response: 404 Not Found**
```json
{
  "error": "ASSET_NOT_FOUND",
  "message": "Asset not found"
}
```

---

## TypeScript Frontend Contract

```typescript
// Status enum for media assets
type MediaAssetStatus = 'PENDING_UPLOAD' | 'UPLOADING' | 'READY' | 'FAILED' | 'DELETED';

// Response from PUT /api/workspaces/{workspaceId}/media/assets/{assetId}
interface PutAssetResponse {
  assetId: string;
  workspaceId: string;
  status: MediaAssetStatus;
  mediaType: string;
  detectedMediaType?: string;
  deduped: boolean;
  uploadUrl?: string | null;
  fileSizeBytes?: number;
  createdAt: string;
}

// Response from POST /api/workspaces/{workspaceId}/media/assets/{assetId}/upload
interface UploadAssetResponse {
  assetId: string;
  workspaceId: string;
  status: 'READY';
  mediaType: string;
  detectedMediaType: string;
  deduped: boolean;
  fileSizeBytes: number;
  createdAt: string;
}

// Response from DELETE /api/workspaces/{workspaceId}/media/assets/{assetId}
interface DeleteAssetResponse {
  deleted: boolean;
  blobScheduledForGC: boolean;
}

// Error response
interface MediaErrorResponse {
  error: string;
  message: string;
  existingFileHash?: string;
  retryAfterSeconds?: number;
}

// Hash computation utility
// NOTE: crypto.subtle.digest() is all-or-nothing — it does not expose incremental SHA-256 state.
// For files >= 100 MB the composable SHALL delegate to a dedicated streaming SHA-256
// implementation (e.g., useFileHash composable backed by SubtleCrypto via a tested
// streaming-hasher abstraction at apply time). The pseudocode below defines the contract only.

async function computeFileHash(file: File): Promise<string> {
  if (file.size < 100 * 1024 * 1024) {
    // < 100 MB: load entire file into memory and hash in one shot
    const buffer = await file.arrayBuffer();
    const hashBuffer = await crypto.subtle.digest('SHA-256', buffer);
    return bytesToHex(hashBuffer);
  }
  // >= 100 MB: delegate to a streaming SHA-256 implementation
  return computeHashStreaming(file); // see contract below
}

// Streaming SHA-256 contract (backend-provided or composable implementation)
async function computeHashStreaming(file: File): Promise<string> {
  // 1. Create a streaming hasher from the composable abstraction (e.g., useFileHash).
  //    The hasher MUST consume ReadableStream chunks and accumulate SHA-256 state
  //    WITHOUT materialising the entire ArrayBuffer in memory.
  //    Example (conceptual — exact API determined at apply):
  //      const hasher = createStreamingHasher();       // init incremental SHA-256
  //      const stream = file.stream();
  //      const reader = stream.getReader();
  //      while (true) {
  //        const { done, value } = await reader.read(); // value is Uint8Array chunk
  //        if (done) break;
  //        await hasher.update(value);                   // feed chunk to hasher
  //      }
  //      const digest = await hasher.digest();           // finalise and return ArrayBuffer
  //      return bytesToHex(digest);
  //
  // 2. The implementation MUST NOT call crypto.subtle.digest() per chunk —
  //    that re-hashes each chunk independently and discards running state.
  //
  // 3. The composable (e.g., useFileHash in the frontend) SHALL expose
  //    `computeHashStreaming(file: File): Promise<string>` as its public API.
  throw new Error('computeHashStreaming: delegate to the streaming hasher composable at apply time');
}

function bytesToHex(buffer: ArrayBuffer): string {
  return Array.from(new Uint8Array(buffer))
    .map(b => b.toString(16).padStart(2, '0'))
    .join('');
}

function sanitizeFilename(name: string): string {
  return name
    .replace(/[/\\]/g, '_')
    .replace(/\.\./g, '_')
    .replace(/\0/g, '')
    .slice(0, 255);
}

function delay(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}

// Main upload flow
async function putAsset(
  file: File,
  workspaceId: string
): Promise<PutAssetResponse | UploadAssetResponse> {
  const assetId = crypto.randomUUID();
  const fileHash = await computeFileHash(file);

  const putResp = await fetch(
    `/api/workspaces/${workspaceId}/media/assets/${assetId}`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        fileHash,
        fileSizeBytes: file.size,
        declaredMediaType: file.type || 'application/octet-stream',
        originalFilename: sanitizeFilename(file.name),
      }),
    }
  );

  if (putResp.status === 202) {
    // Another upload in progress, poll after Retry-After
    const retryAfter = parseInt(putResp.headers.get('Retry-After') || '3', 10);
    await delay(retryAfter * 1000);
    // Retry the PUT
    return putAsset(file, workspaceId);
  }

  if (!putResp.ok) {
    const error: MediaErrorResponse = await putResp.json();
    throw new Error(`PUT failed: ${error.message}`);
  }

  const asset: PutAssetResponse = await putResp.json();

  if (asset.status === 'READY') {
    // Dedup hit — no upload needed
    return asset;
  }

  if (asset.status === 'PENDING_UPLOAD' && asset.uploadUrl) {
    const uploadResp = await fetch(asset.uploadUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/octet-stream' },
      body: file,
    });

    if (!uploadResp.ok) {
      const error: MediaErrorResponse = await uploadResp.json();
      throw new Error(`Upload failed: ${error.message}`);
    }

    // Return the updated asset from upload response
    return await uploadResp.json() as UploadAssetResponse;
  }

  return asset;
}
```

---

## Liquibase Migration

### Changeset: 002-add-workspace-file-blobs-and-media-asset-updates.yaml

```yaml
databaseChangeLog:
  - changeSet:
      id: media-002-add-file-hash-and-related-columns
      author: sdd-apply
      changes:
        - addColumn:
            tableName: media_assets
            columns:
              - column:
                  name: file_hash
                  type: CHAR(64)
                  constraints:
                    nullable: false
              - column:
                  name: detected_media_type
                  type: VARCHAR(64)
              - column:
                  name: failure_reason
                  type: TEXT
              - column:
                  name: upload_started_at
                  type: TIMESTAMP WITH TIME ZONE
              - column:
                  name: updated_at
                  type: TIMESTAMP WITH TIME ZONE
                  defaultValueComputed: CURRENT_TIMESTAMP

  - changeSet:
      id: media-002-make-storage-key-nullable
      author: sdd-apply
      changes:
        - dropNotNullConstraint:
            tableName: media_assets
            columnName: storage_key
            columnDataType: VARCHAR(255)

  - changeSet:
      id: media-002-add-status-check-constraint
      author: sdd-apply
      changes:
        - sql:
            sql: |
              ALTER TABLE media_assets
              ADD CONSTRAINT chk_asset_status
              CHECK (status IN ('PENDING_UPLOAD','UPLOADING','READY','FAILED','DELETED'))

  - changeSet:
      id: media-002-add-hash-format-check-constraint
      author: sdd-apply
      changes:
        - sql:
            sql: |
              ALTER TABLE media_assets
              ADD CONSTRAINT chk_asset_hash_format
              CHECK (file_hash ~ '^[a-f0-9]{64}$')

  - changeSet:
      id: media-002-add-storage-key-invariant-constraint
      author: sdd-apply
      changes:
        - sql:
            sql: |
              ALTER TABLE media_assets
              ADD CONSTRAINT chk_asset_storage_when_ready
              CHECK (status != 'READY' OR storage_key IS NOT NULL)

  - changeSet:
      id: media-002-create-workspace-file-blobs
      author: sdd-apply
      changes:
        - createTable:
            tableName: workspace_file_blobs
            columns:
              - column:
                  name: workspace_id
                  type: varchar(64)
                  constraints:
                    nullable: false
              - column:
                  name: file_hash
                  type: char(64)
                  constraints:
                    nullable: false
              - column:
                  name: storage_key
                  type: varchar(255)
              - column:
                  name: file_size_bytes
                  type: bigint
              - column:
                  name: detected_media_type
                  type: varchar(64)
              - column:
                  name: status
                  type: varchar(20)
                  defaultValue: 'UPLOADING'
                  constraints:
                    nullable: false
              - column:
                  name: failure_reason
                  type: text
              - column:
                  name: orphaned_at
                  type: timestamp with time zone
              - column:
                  name: gc_failure_count
                  type: integer
                  defaultValue: 0
                  constraints:
                    nullable: false
              - column:
                  name: last_gc_attempt_at
                  type: timestamp with time zone
              - column:
                  name: created_at
                  type: timestamp with time zone
                  defaultValueComputed: CURRENT_TIMESTAMP
                  constraints:
                    nullable: false
              - column:
                  name: updated_at
                  type: timestamp with time zone
                  defaultValueComputed: CURRENT_TIMESTAMP
                  constraints:
                    nullable: false
            constraints:
              primaryKey:
                columnNames: workspace_id, file_hash
              foreignKeyName: fk_workspace_file_blobs_workspace
              foreignKeyTableName: workspace_file_blobs
              foreignKeyColumns: workspace_id
              references: workspaces(id)

  - changeSet:
      id: media-002-add-blob-status-check-constraint
      author: sdd-apply
      changes:
        - sql:
            sql: |
              ALTER TABLE workspace_file_blobs
              ADD CONSTRAINT chk_blob_status
              CHECK (status IN ('UPLOADING','READY','FAILED','READY_FOR_GC','GARBAGE_COLLECTED'))

  - changeSet:
      id: media-002-add-blob-hash-format-check-constraint
      author: sdd-apply
      changes:
        - sql:
            sql: |
              ALTER TABLE workspace_file_blobs
              ADD CONSTRAINT chk_blob_hash_format
              CHECK (file_hash ~ '^[a-f0-9]{64}$')

  - changeSet:
      id: media-002-add-blob-storage-when-ready-constraint
      author: sdd-apply
      changes:
        - sql:
            sql: |
              ALTER TABLE workspace_file_blobs
              ADD CONSTRAINT chk_blob_storage_when_ready
              CHECK (status != 'READY' OR storage_key IS NOT NULL)

  - changeSet:
      id: media-002-add-blob-media-type-when-ready-constraint
      author: sdd-apply
      changes:
        - sql:
            sql: |
              ALTER TABLE workspace_file_blobs
              ADD CONSTRAINT chk_blob_media_type_when_ready
              CHECK (status != 'READY' OR detected_media_type IS NOT NULL)

  - changeSet:
      id: media-002-add-blob-hash-format-check-constraint
      author: sdd-apply
      changes:
        - sql:
            sql: |
              ALTER TABLE workspace_file_blobs
              ADD CONSTRAINT chk_blob_hash_format
              CHECK (file_hash ~ '^[a-f0-9]{64}$')

  - changeSet:
      id: media-002-add-blob-detected-media-type-check-constraint
      author: sdd-apply
      changes:
        - sql:
            sql: |
              ALTER TABLE workspace_file_blobs
              ADD CONSTRAINT chk_blob_detected_when_ready
              CHECK (status != 'READY' OR detected_media_type IS NOT NULL)

  - changeSet:
      id: media-002-add-media-asset-blob-fk
      author: sdd-apply
      changes:
        - addForeignKeyConstraint:
            baseTableName: media_assets
            baseColumnNames: workspace_id, file_hash
            constraintName: fk_media_asset_blob
            referencedTableName: workspace_file_blobs
            referencedColumnNames: workspace_id, file_hash

  - changeSet:
      id: media-002-add-blob-indexes
      author: sdd-apply
      changes:
        - createIndex:
            tableName: workspace_file_blobs
            indexName: idx_blobs_status
            columns:
              - column:
                  name: workspace_id
              - column:
                  name: status

  - changeSet:
      id: media-002-add-blob-gc-index
      author: sdd-apply
      changes:
        - sql:
            sql: |
              CREATE INDEX idx_blobs_gc_candidates
              ON workspace_file_blobs(status, orphaned_at)
              WHERE status = 'READY_FOR_GC'

  # Note: FK must be created AFTER both tables exist
  # The FK from media_assets → workspace_file_blobs requires workspace_file_blobs to exist first
  # Therefore, this changeset MUST run after the table creation changeset
```

**Order of Operations:**
1. Create `workspace_file_blobs` table with PK and FK to `workspaces`
2. Add CHECK constraints to `workspace_file_blobs`
3. Add CHECK constraints to `media_assets` (file_hash NOT NULL first)
4. Make `media_assets.storage_key` nullable
5. Add FK from `media_assets` → `workspace_file_blobs`
6. Add indexes

---

## Idempotency Matrix Reference

| Scenario | Behavior | Status Code |
|----------|----------|-------------|
| PUT same assetId + same hash | Return current state | 200 |
| PUT same assetId + different hash | `ASSET_HASH_MISMATCH` error | 409 |
| PUT new asset + blob READY | Dedup, asset → READY | 201 |
| PUT new asset + blob UPLOADING | Wait for other with polling | 202 + Retry-After |
| PUT new asset + blob FAILED | Retry, blob → UPLOADING | 201 |
| PUT new asset + blob READY_FOR_GC | Retry, blob → UPLOADING, clear orphaned_at | 201 |
| PUT new asset + blob GARBAGE_COLLECTED | Retry: blob → UPLOADING, storage_key → null, orphaned_at → null, gc_failure_count → 0; client re-uploads bytes | 201 |
| PUT new asset + blob doesn't exist | Create both, asset → PENDING | 201 |
| POST /upload on PENDING_UPLOAD asset | claimUploadSlot → UPLOADING | 200 |
| POST /upload on UPLOADING asset | Idempotent if same uploader | 200 or 409 |
| POST /upload on READY asset | Idempotent (no-op) | 200 |
| POST /upload on FAILED asset | Allow retry | 200 |
| POST /upload hash mismatch | Temp deleted, blob + asset → FAILED | 422 |
| POST /upload file size mismatch | Temp deleted, blob + asset → FAILED | 422 |
| POST /upload on DELETED asset | 404 | 404 |
| DELETE on READY asset | Soft delete + maybe mark blob READY_FOR_GC | 200 |
| DELETE already DELETED asset | Idempotent | 200 |
| DELETE non-existent asset | 404 | 404 |
| GC on READY_FOR_GC blob after retention | Delete storage object, blob row → GARBAGE_COLLECTED | internal |

---

## Glossary

| Term | Definition |
|------|------------|
| CAS | Content-Addressed Storage — storage model where data is addressed by its cryptographic hash |
| SHA-256 | Secure Hash Algorithm 256-bit — cryptographic hash function used for deduplication |
| Dedup | Deduplication — eliminating duplicate copies of identical data |
| Blob | Binary Large Object — the physical stored file |
| GC | Garbage Collection — the process of reclaiming orphaned blob storage |
| Retention period | 7-day period after which orphaned blobs are physically deleted from storage |
| Magic bytes | First few bytes of a file that identify its format |
