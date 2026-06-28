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
