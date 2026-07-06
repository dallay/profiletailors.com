# Delta for Media Library

> **Terminology note (SB1):** Throughout this specification, "creating" or "reserving" an asset
> refers to the `POST /api/media/assets` operation that creates an asset record in `PROCESSING`
> state.
> The term `PROCESSING` is the authoritative lifecycle state name.

> **Wire format note (R2-C1):** The `mediaType` field is serialized as a MIME string (e.g.,
`image/jpeg`) in all API request and response bodies.

## ADDED Requirements

### Requirement: Media Library Is a Separate Bounded Context

The system MUST implement the media library as a separate bounded context from publishing even
though the MVP continues to serve only the current publishing media behavior.

The media-library bounded context MUST own the creation, upload, browsing, readiness, and retrieval
capabilities for workspace media assets. Publishing MUST consume media assets through the
media-library boundary rather than treating media as an internal publishing subdomain. This change
MUST NOT expand the MVP into a broader digital-asset-management product, and it MUST preserve the
current narrow product scope for publication media.

#### Scenario: Publishing consumes media through a separate bounded context

- GIVEN the product supports attaching uploaded media to publications
- WHEN media capabilities are implemented for this MVP
- THEN the media library MUST be defined as a bounded context separate from publishing
- AND publishing MUST depend on media-library-owned asset capabilities rather than owning those
  capabilities itself

### Requirement: Workspace-Scoped Media Asset Creation

The system MUST expose a workspace-scoped media-library capability for creating uploaded media
assets.

A creation request MUST create a persisted media asset record for the active workspace, assign a
backend-generated stable workspace-scoped storage identity, and return only the metadata needed for
the client to continue the supported upload flow and later reference the asset. The creation model
MUST remain limited to the current MVP publishing media types already accepted by the product. For
this change, the supported media types MUST be explicitly limited to:

- `image/jpeg`
- `image/png`
- `image/gif`
- `image/webp`
- `video/mp4`
- `application/pdf`
- `application/msword`
- `application/vnd.openxmlformats-officedocument.wordprocessingml.document`
- `application/vnd.ms-powerpoint`
- `application/vnd.openxmlformats-officedocument.presentationml.presentation`

The asset identifier MUST be a universally unique identifier (UUID v4 or equivalent
cryptographically random token). Sequential or predictable identifiers are explicitly prohibited to
prevent enumeration of storage keys.

The system MUST NOT introduce folders, tags, titles, albums, ownership roles, or other
digital-asset-management metadata in this change. Duplicate creations MAY occur in the MVP when the
client retries without an idempotency key; the system MUST treat each creation as a separate asset
record and MUST rely on lifecycle cleanup rules for abandoned assets.

When the requested `mediaType` is an OOXML format (`application/msword`,
`application/vnd.openxmlformats-officedocument.wordprocessingml.document`,
`application/vnd.ms-powerpoint`,
`application/vnd.openxmlformats-officedocument.presentationml.presentation`), the `originalFilename`
field MUST be provided and MUST include a recognized OOXML extension (`.doc`, `.docx`, `.ppt`,
`.pptx` respectively). Requests without this extension are rejected before asset creation.

#### Scenario: Workspace member creates an uploaded media asset

- GIVEN an authenticated workspace member acts within an active workspace
- WHEN the client requests creation of an uploaded media asset with a supported media type and file
  metadata
- THEN the system MUST create a workspace-scoped persisted asset record
- AND the asset MUST be backed by a backend-generated stable storage target suitable for later
  upload
- AND the response MUST include the asset identifier and the information required to continue the
  supported upload flow

#### Scenario: Unsupported media type is rejected during creation

- GIVEN an authenticated workspace member acts within an active workspace
- WHEN the client requests creation of an uploaded media asset with a media type outside the current
  MVP publishing envelope
- THEN the system MUST reject the request before storing the asset for publication use
- AND the response MUST identify that the media type is unsupported for the MVP media library

### Requirement: Supported Browser Upload Flow

The system MUST provide a supported browser ingest flow that uploads the selected binary into
platform-managed storage for a previously created uploaded media asset.

For this MVP, the upload flow MUST be implemented as backend-managed upload under workspace-scoped
backend control. The system MUST bind uploaded content to the created asset record, preserve the
original filename and file size when available, and transition the asset into a state that can later
be used by publication workflows. The upload flow MUST enforce the same supported media type
allowlist defined for creation and MUST reject files larger than `500 MB` per asset.

The upload endpoint MUST enforce a maximum request duration of 10 minutes. If the upload stream has
not completed within 10 minutes, the server MUST close the connection and MUST transition the asset
to `FAILED` status.

The upload endpoint MUST stream the request body directly into storage without buffering the full
binary payload in application memory. No in-memory accumulation of upload bytes is permitted at any
layer of the media application stack.

The system MUST enforce the 500 MB limit as early in the request lifecycle as possible: (1) if the
request includes a `Content-Length` header, the server MUST reject the request before reading the
body if `Content-Length` exceeds 500 MB — such rejection MUST use HTTP 413 Payload Too Large; (2)
during streaming, the server MUST maintain a byte counter and terminate the stream with an error
response if the cumulative bytes received exceed 500 MB, without completing storage write.

Server-side validation MUST NOT rely only on client-provided `Content-Type`; it MUST validate the
effective media type using magic-byte inspection as follows:

- JPEG: `FF D8 FF`
- PNG: `89 50 4E 47`
- GIF: `47 49 46 38`
- WEBP: `52 49 46 46` (RIFF header) followed by `57 45 42 50` (WEBP marker)
- MP4: `ftyp` box at byte offset 4

For OOXML formats (`.docx`, `.pptx`, `.doc`, `.ppt`), magic-byte inspection alone is insufficient
because these are ZIP containers with indistinguishable magic bytes (`50 4B 03 04`). Validation MUST
cross-check both the `Content-Type` header and the file extension from `originalFilename`.

Upload retry is allowed only if the asset status is `PROCESSING` or `FAILED`.

#### Scenario: Created asset upload completes successfully

- GIVEN a workspace media asset has been created for uploaded content
- WHEN the client completes the supported upload flow with the selected binary
- THEN the system MUST store the binary in platform-managed storage for that asset
- AND the asset record MUST remain associated with the same workspace and asset identifier
- AND the stored asset MUST become available for later library browsing and publication attachment

#### Scenario: Upload cannot target another workspace asset

- GIVEN a created uploaded media asset belongs to workspace A
- WHEN a client from workspace B attempts to upload content for that asset
- THEN the system MUST reject the upload using the same not-found semantics used for a missing asset
- AND it MUST NOT expose whether the asset exists outside the caller's active workspace context

#### Scenario: Upload retry after interrupted or failed ingest is supported for PROCESSING or FAILED assets

- GIVEN a workspace media asset has been created and its previous upload attempt failed or was
  interrupted before the asset became `READY`
- WHEN the client retries the supported upload flow for that same asset
- THEN the system MUST allow the upload only if the asset status is `PROCESSING` or `FAILED`
- AND the successful retry MUST preserve the same asset identifier and workspace association

#### Scenario: FAILED asset can be retried

> For FAILED asset retry behavior, see the lifecycle section 'Minimal Asset Lifecycle for MVP Reuse'
> which defines the canonical FAILED → PROCESSING transition.

#### Scenario: Ready asset cannot be uploaded again

- GIVEN a workspace media asset has already completed upload successfully and is `READY` for use
- WHEN the client attempts to upload binary content to that asset again
- THEN the system MUST reject the request
- AND it MUST require the client to create a new asset if a different binary is needed

#### Scenario: Partial or interrupted upload does not leave the asset ready

- GIVEN a workspace media asset is receiving uploaded content
- WHEN the upload stream is interrupted or storage persistence fails before completion
- THEN the system MUST NOT mark the asset as `READY`
- AND the asset MUST transition to `FAILED`
- AND the system MUST attempt to delete the partial storage object from the configured bucket
- AND if the storage delete succeeds, the asset MUST be in `FAILED` state
- AND if the storage delete itself fails, the asset MUST still transition to `FAILED` and the
  cleanup failure MUST be logged with sufficient context (assetId, storageKey, error) for the stale
  reconciler to attempt cleanup on its next run

#### Scenario: Storage write succeeds but metadata transition fails

- GIVEN a binary has been stored successfully for an uploading asset
- WHEN the metadata transition to `READY` fails before completion
- THEN the system MUST attempt to delete the storage object
- AND MUST transition the asset to `FAILED`
- AND MUST NOT leave the asset in `PROCESSING` with a binary present in storage

#### Scenario: Concurrent upload attempt against the same active or ready asset is rejected

- GIVEN a workspace media asset already has status `PROCESSING` with an active upload in progress (
  i.e., `uploadStartedAt` was set within the past 30 minutes), OR status `READY`
- WHEN another upload request targets the same asset
- THEN the system MUST reject the request with HTTP 409 `ASSET_UPLOAD_CONFLICT`

#### Scenario: Stale PROCESSING asset without active upload allows new upload

- GIVEN a workspace media asset has status `PROCESSING` but no active upload in progress (i.e.,
  `uploadStartedAt` is null or older than 30 minutes)
- WHEN an upload request targets that asset
- THEN the system MUST allow the upload (the asset is awaiting its first upload or is a stale
  PROCESSING state)

### Requirement: Email Verification Required for Media Upload

The system MUST require `emailStatus = VERIFIED` before an authenticated user can create or upload media assets to the workspace media library.

This policy MUST align with other verification-gated product capabilities so unverified users receive the same denial reason and no media asset is created or uploaded on their behalf.

#### Scenario: Unverified user cannot create uploadable asset

- GIVEN an authenticated user with `emailStatus = UNVERIFIED`
- WHEN the user requests media asset creation or upload
- THEN the system MUST deny the request
- AND the denial MUST indicate email verification is required

#### Scenario: Verified user can proceed with media upload flow

- GIVEN an authenticated user with `emailStatus = VERIFIED`
- WHEN the user requests media asset creation or upload with otherwise valid data
- THEN the system MUST evaluate the request under normal media-library rules

### Requirement: Rate Limiting for Upload Operations

The system MUST enforce rate limits on the upload flow at workspace scope to prevent abuse and
unbounded incomplete asset buildup.

Per-workspace limits MUST be enforced:

- Maximum 5 concurrent uploads per workspace at any given time
- Maximum 200 asset creation requests per workspace per hour

Rate limits MUST be enforced consistently across all backend instances using a distributed
enforcement mechanism, not per-instance in-memory counters. Concurrent upload enforcement MUST be
atomic across all backend instances (e.g., via a row-level lock or atomic increment on a
per-workspace counter record).

When a rate limit is exceeded, the server MUST respond with HTTP 429 and a `Retry-After` header
indicating when the client may retry.

#### Scenario: Workspace exceeds concurrent upload limit

- GIVEN a workspace already has 5 uploads in progress simultaneously
- WHEN a sixth upload request is made for that workspace
- THEN the system MUST reject the request with HTTP 429
- AND the response MUST include a `Retry-After` header

#### Scenario: Workspace exceeds hourly creation limit

- GIVEN a workspace has already made 200 asset creation requests in the current hour
- WHEN another creation request is made for that workspace
- THEN the system MUST reject the request with HTTP 429
- AND the response MUST include a `Retry-After` header

### Requirement: Workspace Media Library Browsing

The system MUST expose workspace-scoped list and read capabilities for persisted media assets from
the media-library bounded context so the SPA can browse and select previously uploaded assets.

The list capability MUST return only assets belonging to the active workspace and MUST order results
newest-first for the MVP unless a more specific product requirement is later approved. By default,
the list endpoint MUST filter to only `READY` status assets. The caller MAY override this by
providing an explicit `status` query parameter. The list endpoint MUST support explicit `status`
filtering with at minimum the following values: `READY`, `PROCESSING`, `FAILED`. Multiple status
values MAY be combined in a single request using comma-separated or repeatable query parameters. The
list capability MUST support an explicit `status=PROCESSING` filter so the SPA can surface
in-progress or dangling uploads from previous sessions to the user. The list capability MUST be
paginated with a bounded page size so responses remain operationally safe for large libraries. The
read model MUST remain provider-neutral and limited to metadata already needed for upload and
publishing flows. The list and read capabilities MUST NOT require search, folders, tags, smart
organization, or cross-workspace sharing in this change.

#### Scenario: Media library list returns only workspace assets

- GIVEN workspace A and workspace B both contain persisted media assets
- WHEN a workspace A member requests the media library list
- THEN the response MUST include only assets from workspace A
- AND assets from workspace B MUST NOT appear in the result

#### Scenario: Media library list is newest-first

- GIVEN a workspace has multiple persisted media assets created at different times
- WHEN the workspace member requests the media library list
- THEN the system MUST return the assets in newest-first order for the MVP browsing experience

#### Scenario: Reading a single asset returns persisted metadata

- GIVEN a workspace member knows the identifier of a media asset in the active workspace
- WHEN the member requests that asset
- THEN the system MUST return the persisted metadata needed to identify and attach the asset
- AND it MUST NOT expose provider secrets or unrelated storage implementation details

#### Scenario: Reading an asset outside the active workspace does not leak existence

- GIVEN a media asset belongs to workspace A
- WHEN a client acting in workspace B requests that asset identifier
- THEN the system MUST respond using the same not-found semantics used for a missing asset
- AND it MUST NOT expose whether the asset exists outside the caller's active workspace context

#### Scenario: Media library list remains bounded for large workspaces

- GIVEN a workspace contains more persisted media assets than a single response page can safely
  return
- WHEN the workspace member requests the media library list
- THEN the system MUST return a bounded page of assets in newest-first order
- AND the response MUST include enough paging information for the client to request the next page

### Requirement: Minimal Asset Lifecycle for MVP Reuse

The media-library MVP MUST provide the first centralized workspace media catalog as part of the
separate media bounded context.

An asset that has not completed the supported upload flow SHALL NOT be treated as `READY` for
publication attachment. The system MUST expose enough status information for the client to
distinguish `PROCESSING`, `READY`, and `FAILED` assets. Assets created for upload MUST begin in
`PROCESSING` state and MUST only become `READY` after successful binary persistence. `FAILED` assets
are retryable; the state transition for retry is `FAILED` → `PROCESSING`. The client does not need
to create a new asset to retry a failed upload.

`PROCESSING` assets with `created_at` older than 2 hours MUST be transitioned to `FAILED` by a
scheduled stale reconciler. The reconciler MUST run at minimum every 15 minutes. The reconciler MUST
NOT transition assets whose last upload activity occurred within the past 30 minutes (grace period
for large uploads in progress).

The system MUST define cleanup behavior for abandoned incomplete assets so stale `PROCESSING`
records do not accumulate indefinitely. Delete semantics are out of scope for this specification and
MUST NOT be exposed by the MVP API surface in this change. Internal persistence reuse with existing
publishing-era storage structures MAY occur as an implementation detail, but it MUST NOT change the
media library's bounded-context ownership in this specification.

Asset lifecycle state transitions:

- `PROCESSING` → `READY` (upload completes successfully)
- `PROCESSING` → `FAILED` (upload fails, is interrupted, times out, or is cleaned up by the stale
  reconciler)
- `FAILED` → `PROCESSING` (client retries upload for the same asset)

#### Scenario: Incomplete asset is not treated as ready for reuse

- GIVEN a workspace media asset has been created but its upload has not completed successfully
- WHEN the client browses the workspace media library or attempts to attach that asset
- THEN the system MUST NOT treat the asset as `READY` for publication use
- AND the asset state exposed to the client MUST distinguish it from a successfully uploaded asset

#### Scenario: Abandoned incomplete asset is eventually cleaned up from active flow

- GIVEN a workspace media asset remains in `PROCESSING` beyond 2 hours with no recent upload
  activity within the 30-minute grace period
- WHEN the stale reconciler runs
- THEN the asset MUST transition from `PROCESSING` to `FAILED`
- AND it MUST no longer appear as an indefinitely active upload in client flows

#### Scenario: FAILED asset can be retried without creating a new asset

- GIVEN a workspace media asset has status `FAILED` due to a failed or interrupted upload
- WHEN the client retries the upload for that same asset
- THEN the system MUST accept the retry by transitioning the asset from `FAILED` to `PROCESSING`
- AND proceed with the upload flow without requiring the client to create a new asset

> **Note (Fix 1):** The system MUST also reset any in-flight upload tracking (equivalent to
`uploadStartedAt = NULL`) when transitioning to `FAILED`, so that the FAILED asset is immediately
> eligible for retry without a cooldown.

#### Scenario: Concurrent retry attempts against the same FAILED asset

- GIVEN a workspace media asset has status `FAILED`
- WHEN two concurrent upload requests target the same asset simultaneously
- THEN only one MUST succeed in transitioning to the upload-in-progress state
- AND the other MUST receive HTTP 409 `ASSET_UPLOAD_IN_PROGRESS`

### Requirement: DeleteWorkspaceAssetHandler — Storage Delete + DB Soft-Delete in Atomic Transaction

The system MUST wrap the `mediaAssetRepository.softDelete()` call inside `transactionRunner.runAtomically {}` after the storage delete succeeds, so that DB soft-delete commits or rolls back as an atomic unit.

The storage delete operation MUST be executed first and outside the transaction because it is the irreversible operation. If the storage delete succeeds but the DB soft-delete fails, the system MUST schedule an asynchronous blob cleanup job rather than propagating the failure to the caller.

The async cleanup job MUST be idempotent and MUST NOT block the delete response from returning successfully to the caller once the storage object has been deleted.

#### Scenario: Storage delete and DB soft-delete both succeed

- GIVEN an authenticated workspace member deletes an existing `READY` media asset
- WHEN `DeleteWorkspaceAssetHandler` executes
- THEN `storageApplicationService.delete()` MUST be called first
- AND if storage delete succeeds, `transactionRunner.runAtomically { mediaAssetRepository.softDelete() }` MUST be executed
- AND the asset MUST be soft-deleted in the database
- AND the response MUST indicate deletion success

#### Scenario: Storage delete succeeds but DB soft-delete fails — async cleanup scheduled

- GIVEN an authenticated workspace member deletes an existing `READY` media asset
- AND the storage delete completes successfully
- WHEN `mediaAssetRepository.softDelete()` throws inside `runAtomically {}`
- THEN the transaction MUST be rolled back
- AND an async blob cleanup job MUST be scheduled with sufficient context (assetId, storageKey, workspaceId)
- AND the response MUST indicate deletion success (storage is already deleted)

#### Scenario: Storage delete fails — operation fails without DB change

- GIVEN an authenticated workspace member deletes an existing `READY` media asset
- WHEN `storageApplicationService.delete()` throws
- THEN the handler MUST NOT call `softDelete()`
- AND the asset MUST remain in its current database state
- AND the error MUST be propagated to the caller

### Requirement: UploadAssetHandler — Atomic State Transition and Slot Release

The system MUST wrap `mediaAssetRepository.markAsReady()` inside `transactionRunner.runAtomically {}` so the state transition commits or rolls back as an atomic unit.

The slot release (`releaseConcurrentUploadSlot()`) MUST be executed in a `finally` block after the transaction completes, so the slot is always released regardless of whether the upload succeeded or failed. Slot release is idempotent and MUST NOT be part of the atomic transaction — if the transaction rolls back, the slot remains held by the asset, allowing the client to retry.

The slot claim (`claimConcurrentUploadSlot()`) MUST remain outside the transaction because it is a pre-condition check and is idempotent. The S3 upload (`uploadWithStreamingValidation()`) MUST remain outside the transaction because it is an external operation with no DB involvement.

If the transaction rolls back, the upload MUST be considered failed and the cleanup semantics defined in the media-library spec apply.

#### Scenario: Upload completes and markAsReady commits atomically; slot released in finally

- GIVEN an asset is in `UPLOADING` status with a valid upload slot claimed
- AND the S3 upload via `uploadWithStreamingValidation()` completes successfully
- WHEN the atomic block executes `markAsReady()`
- THEN `markAsReady()` MUST commit
- AND the asset MUST transition to `READY`
- AND the `finally` block MUST release the concurrent upload slot

#### Scenario: Slot claim stays outside transaction (pre-condition)

- GIVEN a workspace has 5 uploads in progress
- WHEN a sixth upload request is made
- THEN `claimConcurrentUploadSlot()` MUST be evaluated outside any transaction
- AND the request MUST be rejected with HTTP 429 before any atomic block is entered

### Requirement: PutAssetHandler — Atomic Blob and Asset Creation for Created Path

The system MUST wrap `workspaceFileBlobRepository.upsertBlob()` and `createPendingAsset()` (which includes `mediaAssetRepository.create()`) inside `transactionRunner.runAtomically {}` for the `handleNewBlob` code path only.

If the blob upsert succeeds but the asset creation fails, the transaction MUST roll back and revert the blob upsert, preventing orphaned blob records.

The `handleExistedBlob` path already uses `transactionRunner.runAtomically {}` correctly and MUST NOT be modified.

#### Scenario: New blob and new asset both commit atomically

- GIVEN no blob exists for `(workspaceId, fileHash)`
- WHEN a client calls `PUT /api/workspaces/{workspaceId}/media/assets/{assetId}` with new file content
- THEN `transactionRunner.runAtomically { upsertBlob(); createPendingAsset() }` MUST be executed
- AND the blob row MUST be inserted with status `UPLOADING`
- AND the asset row MUST be inserted with status `PENDING_UPLOAD`
- AND both inserts MUST commit together

#### Scenario: Blob upsert succeeds but asset creation fails — blob upsert rolled back

- GIVEN no blob exists for `(workspaceId, fileHash)`
- AND `workspaceFileBlobRepository.upsertBlob()` succeeds inside the atomic block
- WHEN `createPendingAsset()` throws
- THEN the transaction MUST roll back
- AND the blob upsert MUST be reverted
- AND no orphaned blob record MUST exist

#### Scenario: handleExistedBlob path unchanged

- GIVEN `PutAssetHandler` detects an existing blob for `(workspaceId, fileHash)` with status `READY`
- WHEN the `handleExistedBlob` path is executed
- THEN `transactionRunner.runAtomically {}` MUST already be in use at that code path
- AND this change MUST NOT modify that existing transactional behavior

### Requirement: External Source Type and Attribution Fields

The system MUST extend the `MediaAsset` model and the `media_assets` schema to support attribution
metadata for externally-imported media assets. This enables downstream changes (e.g., the Unsplash
provider) to populate provenance fields without further schema negotiation.

The `MediaSourceType` enum MUST contain exactly `UPLOADED` and `EXTERNAL`. The vestigial
`EXTERNAL_URL` value MUST be removed from the media-context enum. The publishing-context
`AssetSourceType.EXTERNAL_URL` is NOT modified.

The `media_assets` table MUST gain six nullable columns: `source_provider VARCHAR(32)`,
`external_id VARCHAR(255)`, `source_url VARCHAR(2048)`, `author_name VARCHAR(255)`,
`author_url VARCHAR(2048)`, and `metadata JSONB`. Two CHECK constraints MUST be added:
`chk_asset_uploaded_implies_no_provider` (UPLOADED rows MUST have NULL `source_provider`) and
`chk_asset_external_implies_provider_and_id` (EXTERNAL rows MUST have non-null `source_provider`
and `external_id`).

The migration MUST be additive only — no backfill, no UPDATE statements, no changes to existing
rows.

No DB-side enum, FK, or UNIQUE constraint SHALL be added on `source_provider` or `external_id`.

The frontend `MediaSourceType` union and `MediaAssetSummary` MUST be extended to match the backend
contract. The frontend MUST NOT render attribution from `authorName` / `authorUrl` in this change.

`MediaAssetResponse` and `MediaAssetSummary` MUST surface all six new fields as nullable,
defaulting to `null` for legacy rows.

The `MediaAsset.init` block MUST enforce per-`sourceType` rules:
- `UPLOADED` ⇒ `sourceProvider` MUST be `null`.
- `EXTERNAL` ⇒ `sourceProvider` and `externalId` MUST be non-blank.
- When `sourceProvider` is non-null, it MUST match `^[a-z][a-z0-9_]{0,31}$`.

The CAS binary path MUST be shared between browser uploads and provider imports. The difference
between `UPLOADED` and `EXTERNAL` assets is the metadata attached to the row, NOT the binary flow.
Provider imports MUST construct the `media_assets` row with `source_type='EXTERNAL'` from the
very first INSERT.

The project-owner architectural approval of the bounded-context enum split (`MediaSourceType`
vs `AssetSourceType.EXTERNAL_URL`) MUST be recorded in the verify report before the change
advances to `sdd-archive`.

#### Scenario: External source type is available on MediaSourceType

- GIVEN the media-context `MediaSourceType` enum
- WHEN the enum is enumerated
- THEN it MUST contain exactly `UPLOADED` and `EXTERNAL`
- AND `EXTERNAL_URL` MUST NOT exist

#### Scenario: Schema adds six nullable attribution columns with CHECK constraints

- GIVEN a PostgreSQL `media_assets` table at the post-`004` Liquibase head
- WHEN the changeset `media-005-add-external-metadata` runs
- THEN the table MUST contain the six new nullable columns with the documented types
- AND two CHECK constraints MUST be created
- AND the migration must be additive with no backfill

#### Scenario: UPLOADED rows reject a non-null source_provider

- GIVEN a row in `media_assets` with `source_type='UPLOADED'` and `source_provider='unsplash'`
- WHEN the row is INSERTed
- THEN the database MUST raise a CHECK violation

#### Scenario: EXTERNAL rows reject NULL source_provider or external_id

- GIVEN a row with `source_type='EXTERNAL'` and `source_provider=NULL` or `external_id=NULL`
- WHEN the row is INSERTed
- THEN the database MUST raise a CHECK violation

#### Scenario: MediaAsset.init rejects EXTERNAL with blank/null fields

- GIVEN `sourceType=EXTERNAL` and `sourceProvider=null` or `externalId=null`
- WHEN `MediaAsset.init` evaluates
- THEN it MUST throw `IllegalArgumentException`

#### Scenario: MediaAsset.init validates sourceProvider format

- GIVEN `sourceType=EXTERNAL` with `sourceProvider` set to an invalid format
- WHEN `MediaAsset.init` evaluates
- THEN it MUST throw `IllegalArgumentException` with a message referencing the regex
- AND `sourceProvider='unsplash'` MUST NOT throw

#### Scenario: Response and summary DTOs surface all six new nullable fields

- GIVEN a `MediaAsset` with all six external-metadata fields populated
- WHEN the DTO serializes
- THEN the JSON body MUST include all six fields
- AND for a legacy row with all six fields null, the DTO MUST serialize them as `null`

#### Scenario: Frontend types mirror the backend contract

- GIVEN the SPA source after the change
- WHEN `vue-tsc --build` runs
- THEN the `MediaSourceType` union MUST include `'EXTERNAL'`
- AND `MediaAssetSummary` MUST declare the six new optional fields

#### Scenario: Frontend does not render attribution in this change

- GIVEN the SPA source after the change applies
- WHEN the `.vue` files are searched for `authorName` or `authorUrl`
- THEN zero `.vue` references SHALL consume those fields for display

#### Scenario: CAS binary path is shared between browser uploads and provider imports

- GIVEN a browser upload and an Unsplash provider import
- WHEN both persist their bytes through the CAS pipeline
- THEN both go through the same CAS path
- AND the provider-import row MUST INSERT with `source_type='EXTERNAL'` from the first call

#### Scenario: Project-owner architectural approval recorded

- GIVEN the change's Definition of Done
- WHEN `sdd-verify` runs
- THEN the verify report MUST contain a verbatim record of the project owner's approval

### Requirement: MediaProvider port

The `media-library` bounded context MUST expose a `MediaProvider` port with `search` and
`import` operations, both returning provider-neutral types. Implementations live in their
own bounded context (the first shipped adapter is `mediaprovider.unsplash`).

#### Scenario: Port is the public surface for providers

- GIVEN a future provider adapter (Pexels, Giphy, etc.)
- WHEN adding it to the system
- THEN `media-library` MUST NOT require source edits beyond application configuration

### Requirement: Provider imports share the CAS binary path

The import flow MUST reuse `workspaceFileBlobRepository.upsertBlob()` and
`createPendingAsset()` to persist provider-imported binaries. Provider imports MUST
populate `source_type='EXTERNAL'`, `source_provider`, `external_id`, and the six
attribution columns atomically with the row insert. The binary pipeline itself MUST be
unchanged from the upload path.

#### Scenario: Re-import deduplicates to the canonical asset

- GIVEN a workspace already stores the bytes of a provider photo
- WHEN the same photo is re-imported
- THEN the response MUST return `deduped: true`
- AND it MUST reference the canonical existing active `media_assets` row for that workspace
- AND it MUST reuse the existing blob rather than creating a duplicate blob or asset row

### Requirement: Provider import requires verified email and rate limits

`ImportExternalAsset` MUST be guarded by `EmailVerifiedGuard`, the per-workspace rate
limiter, and the same concurrent-slot guard used by uploads. Provider imports MUST count
against the concurrent-slot limit.

#### Scenario: Unverified email is rejected

- GIVEN a workspace member without verified email
- WHEN they request an import
- THEN the response MUST be the same status that an unverified upload receives

#### Scenario: Concurrent upload slot shared

- GIVEN the workspace has five in-flight uploads
- WHEN a member requests an import
- THEN the import MUST be rejected with 429 until a slot frees up

### Requirement: Upload Retry After Failed Atomic Block

When the `markAsReady()` atomic block rolls back due to a failure, the `finally` block releases the concurrent upload slot. The client MUST re-claim a slot to retry, subject to the same concurrency and rate-limit checks as a fresh upload.

#### Scenario: Atomic block rolls back — client retries upload

- GIVEN an asset is in `UPLOADING` status after `markAsReady()` rolled back
- AND the `finally` block released the concurrent upload slot
- WHEN the client retries the upload
- THEN the system MUST allow the retry if the asset status is `UPLOADING`
- AND the client MUST re-claim a concurrent upload slot to proceed

---

## Non-Functional Requirements — Transactional Boundaries

### Rollback Behavior

| Operation | What Rolls Back | What Does NOT Roll Back |
|-----------|-----------------|------------------------|
| `DeleteWorkspaceAssetHandler` | DB soft-delete (if scheduled cleanup succeeds) | Storage delete (irreversible) |
| `UploadAssetHandler` | `markAsReady()` | Slot claim (idempotent pre-condition) |
| `PutAssetHandler` `handleNewBlob` | Blob upsert + asset creation | Nothing (all DB) |

### Async Cleanup Semantics

When `DeleteWorkspaceAssetHandler` schedules async cleanup after a partial failure:
- The job MUST be enqueued with `(assetId, workspaceId, storageKey)` context
- The job MUST be idempotent — safe to execute multiple times
- The job MUST be retriable with backoff on transient failures
- Cleanup failure MUST be logged with sufficient context for the stale reconciler to pick up

### Constraints — What MUST NOT Change

1. The `handleExistedBlob` path at line 781 in `PutAssetHandler` MUST NOT be modified — it already correctly uses `transactionRunner.runAtomically {}`
2. Storage application service behavior MUST NOT change — only the DB call ordering changes
3. `claimConcurrentUploadSlot()` MUST remain outside the transaction as a pre-condition guard
4. `uploadWithStreamingValidation()` MUST remain outside the transaction — S3 operations cannot participate in R2DBC transactions
5. No schema changes — all changes are at the application layer only
6. The `StaleAssetReconciler` behavior is out of scope and already uses `runAtomically` correctly

---

## Integration Test Scenarios — Transactional Boundaries

The integration test suite MUST verify the following with a real Postgres database:

### DeleteWorkspaceAssetHandler
- [ ] Storage delete succeeds + DB soft-delete succeeds → asset is `DELETED`
- [ ] Storage delete succeeds + DB soft-delete fails → cleanup job is scheduled, response is success
- [ ] Storage delete fails → `DELETED` status is NOT set, error is propagated

### UploadAssetHandler
- [ ] Happy path: `markAsReady()` + `releaseConcurrentUploadSlot()` both succeed and commit
- [ ] `markAsReady()` succeeds, `releaseConcurrentUploadSlot()` fails → transaction rolls back, asset stays `UPLOADING`
- [ ] Slot claim outside transaction: 6th concurrent upload is rejected before entering atomic block

### PutAssetHandler (handleNewBlob only)
- [ ] New blob + new asset both commit atomically
- [ ] Blob upsert succeeds + asset creation fails → blob upsert is rolled back, no orphaned blob
- [ ] `handleExistedBlob` path (line 781) is NOT affected by the change
