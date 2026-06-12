# Publishing Specification

## Purpose

Define workspace-scoped social publishing behavior for Profile Tailors. This specification
establishes the provider-neutral contracts for connected social accounts, publications, scheduling,
queue execution, retry handling, and provider delivery seams, with LinkedIn personal-profile
publishing as the first implemented provider slice.

## Requirements

### Requirement: Workspace-Scoped Social Connections

The system MUST allow an authenticated workspace member to register and manage a social-provider
connection in workspace scope.

A social connection MUST be associated with exactly one workspace and one provider account identity.
The system MUST persist enough provider metadata to identify the connected account, provider type,
connection status, and credential freshness. Provider credential secrets MUST remain an
infrastructure concern and MUST NOT leak into public API responses. LinkedIn personal-profile
connection support MUST be implemented in this change. LinkedIn page support MAY be added later
without redefining the core connection model.

#### Scenario: User connects a LinkedIn personal profile to a workspace

- GIVEN an authenticated USER acts within an active workspace
- AND the user completes the supported LinkedIn OAuth flow successfully
- WHEN the backend finalizes the provider connection
- THEN the system MUST persist a workspace-scoped social connection for provider `LINKEDIN`
- AND the persisted connection MUST identify the connected personal profile account

#### Scenario: Provider credential details are not exposed through public read models

- GIVEN a workspace member retrieves connected social account details
- WHEN the system returns the connection read model
- THEN the response MUST include only safe provider metadata and connection status
- AND it MUST NOT expose provider access tokens, refresh tokens, or equivalent secrets

### Requirement: Provider-Neutral Publication Lifecycle

The system MUST model publications independently from provider-specific transport details.

A publication MUST belong to one workspace, one authoring principal, and one or more target provider
accounts. The publication lifecycle MUST support at least the states DRAFT, QUEUED, SCHEDULED,
PROCESSING, PUBLISHED, FAILED, and CANCELLED. The system MUST allow future approval-oriented
lifecycle extensions without redefining the core publication identity.

#### Scenario: Draft publication becomes queued for immediate delivery

- GIVEN an authenticated workspace member creates a valid publication draft for a connected LinkedIn
  profile
- WHEN the user requests immediate publication
- THEN the system MUST transition the publication into a queued delivery path
- AND the publication MUST become eligible for worker processing without requiring a second manual
  action

#### Scenario: Publication state prevents duplicate completion semantics

- GIVEN a publication has already reached terminal state `PUBLISHED` or `CANCELLED`
- WHEN a later operation attempts to reapply an incompatible lifecycle transition
- THEN the system MUST reject the invalid transition
- AND it MUST preserve the existing terminal state

### Requirement: Scheduling Modes and Queue Ordering

The system MUST support explicit scheduling strategies for outbound publication delivery.

The supported strategies in this change MUST be `NOW`, `SCHEDULED_AT`, `NEXT_SLOT`, and priority
queue ordering. `NOW` MUST enqueue a delivery job immediately. `SCHEDULED_AT` MUST make the job due
at the requested date-time. `NEXT_SLOT` MUST resolve the next available publishing slot according to
the workspace scheduling policy in effect for that account. Priority delivery MUST order otherwise
eligible jobs ahead of non-priority jobs without bypassing authorization or validity checks.

#### Scenario: Scheduled publication waits until due time

- GIVEN a valid publication is created with scheduling mode `SCHEDULED_AT`
- WHEN the due time has not yet arrived
- THEN the publication MUST remain scheduled and not be delivered
- AND the worker MUST ignore it for claim until it becomes due

#### Scenario: Priority publication moves ahead of regular queue work

- GIVEN two due publication jobs target the same provider account and one is marked priority
- WHEN the worker selects the next eligible job to claim
- THEN the system MUST choose the priority job first
- AND both jobs MUST still respect their overall validity and claim rules

### Requirement: Editable and Cancellable Pre-Delivery Publications

The system MUST allow editing and cancellation before a publication is claimed for delivery.

A publication in DRAFT, QUEUED, or SCHEDULED state MAY be edited, including text, media references,
schedule mode, and schedule timing, as long as the delivery job has not been claimed for processing.
Such a publication MAY also be cancelled before claim. Once processing has begun, the system MUST
prevent unsafe edits that would invalidate the claimed delivery attempt.

#### Scenario: Queued publication is edited before claim

- GIVEN a publication is queued and not yet claimed by a worker
- WHEN the user edits the text or scheduling data
- THEN the system MUST persist the new publication content and delivery metadata
- AND the previous unclaimed job representation MUST no longer be treated as authoritative

#### Scenario: Processing publication cannot be cancelled retroactively

- GIVEN a worker has already claimed a publication job for delivery
- WHEN the user attempts to cancel the publication
- THEN the system MUST reject cancellation for that in-flight attempt
- AND it MUST preserve deterministic processing semantics

### Requirement: Delivery Attempts, Retries, and Failure Recovery

The system MUST persist delivery attempts and apply bounded automatic retry behavior.

Every provider delivery attempt MUST be recorded with attempt order, provider target, execution
time, and outcome. When provider delivery fails with a retryable error, the system MUST
automatically reschedule another attempt until the configured retry budget is exhausted. When the
retry budget is exhausted, the publication MUST be marked FAILED. A failed publication MUST support
later manual retry or rescheduling by an authorized workspace member.

#### Scenario: Retryable provider failure is retried automatically

- GIVEN a due publication job is claimed for delivery
- AND the provider adapter returns a retryable failure outcome
- WHEN the retry budget has not been exhausted
- THEN the system MUST record the failed attempt
- AND it MUST schedule a later retry attempt rather than marking the publication terminally failed

#### Scenario: Exhausted retry budget leaves publication failed but recoverable

- GIVEN a publication delivery has reached the configured retry limit
- WHEN the final retryable or non-retryable failure is recorded
- THEN the system MUST mark the publication as FAILED
- AND an authorized user MUST be able to trigger manual retry or rescheduling later

### Requirement: Media Asset Sources and Provider Capability Validation

The system MUST support both backend-managed uploads and external media references.

A publication asset MAY originate from an uploaded backend-managed file or an external URL. The
system MUST persist asset source metadata separately from provider-delivery metadata. Before
dispatching a publication, the system MUST validate that the targeted provider account and content
shape are compatible with the provider capabilities implemented for that slice. This change MUST
implement LinkedIn personal-profile capability validation for the MVP-supported content formats.

#### Scenario: Uploaded asset is prepared for provider delivery

- GIVEN a publication references a backend-managed asset
- WHEN the publication becomes ready for provider delivery
- THEN the system MUST resolve the stored asset metadata for the provider adapter
- AND the adapter MUST use that metadata to perform provider-specific media registration or upload
  steps

#### Scenario: Unsupported provider-content combination is rejected before queue execution

- GIVEN a publication targets a provider account with a content shape not supported by the
  implemented capability set
- WHEN the publication is validated for queueing or delivery
- THEN the system MUST reject the publication as invalid for that provider target
- AND it MUST NOT enqueue a job that cannot succeed under known capability rules

### Requirement: Asset Ingest API

The system MUST provide an API endpoint for workspace members to create `PublicationAsset` records.

A workspace member MUST be able to submit a `CreateAssetCommand` containing `workspaceId`, `storageKey` (for uploaded assets), `mediaType`, and optionally `externalUrl` (for external assets). The system MUST validate the media type against LinkedIn-supported types and enforce the 10MB per-asset size limit. Upon successful creation, the asset record MUST be persisted with status `READY` and a unique `assetId`.

#### Scenario: Workspace member uploads a media asset

- GIVEN an authenticated workspace member with a connected LinkedIn account
- WHEN the member submits a `CreateAssetCommand` with `storageKey` pointing to a valid uploaded file and `mediaType: IMAGE/JPEG`
- THEN the system MUST persist a `PublicationAsset` record with `sourceType: UPLOADED`, `mediaType: IMAGE/JPEG`, `storageKey`, and `status: READY`
- AND the asset MUST be assigned a unique `assetId`

#### Scenario: Workspace member registers an external URL asset

- GIVEN an authenticated workspace member with a connected LinkedIn account
- WHEN the member submits a `CreateAssetCommand` with `externalUrl` and `mediaType: IMAGE/PNG`
- THEN the system MUST persist a `PublicationAsset` record with `sourceType: EXTERNAL_URL`, `mediaType: IMAGE/PNG`, `externalUrl`, and `status: READY`

#### Scenario: Asset creation rejects unsupported media type

- GIVEN an authenticated workspace member submits a `CreateAssetCommand` with `mediaType: APPLICATION/PDF`
- WHEN the system validates the media type against LinkedIn-supported types
- THEN the system MUST reject the request with a validation error
- AND no `PublicationAsset` record MUST be created

#### Scenario: Asset creation rejects file exceeding size limit

- GIVEN an authenticated workspace member submits a `CreateAssetCommand` with a `storageKey` referencing a file larger than 10MB
- WHEN the system validates the file size
- THEN the system MUST reject the request with a validation error
- AND no `PublicationAsset` record MUST be created

### Requirement: AssetUploader Port

The system MUST provide an `AssetUploader` port interface that provider adapters implement to register and upload assets to their respective platforms.

The `AssetUploader` interface MUST declare: `suspend fun uploadAsset(asset: PublicationAsset, content: Flow<ByteArray>): ProviderAssetRef`. The port MUST be implemented by `RealLinkedInAssetUploader` for production and `FakeLinkedInAssetUploader` for testing.

#### Scenario: AssetUploader implementation returns provider asset reference

- GIVEN a `PublicationAsset` with `sourceType: UPLOADED` and a valid `storageKey`
- WHEN `AssetUploader.uploadAsset()` is called with the asset and its binary content flow
- THEN the uploader MUST return a `ProviderAssetRef` containing the provider-specific `providerAssetId` (LinkedIn URN), `mediaType`, and optionally `accessUrl`
- AND the LinkedIn URN format MUST be `urn:li:digitalmediaAsset:{asset-type}:{asset-id}`

### Requirement: RealLinkedInAssetUploader Two-Phase Upload

The `RealLinkedInAssetUploader` MUST implement the LinkedIn asset upload flow in two distinct phases.

**Phase 1 — Register**: The uploader MUST call `POST /assets` to register the asset and receive a `digitalmediaAsset` URN plus an upload URL. **Phase 2 — Stream**: The uploader MUST `PUT` the binary content directly to the upload URL obtained in Phase 1. The uploader MUST return a `ProviderAssetRef` with the URN as `providerAssetId`.

#### Scenario: LinkedIn asset uploader completes two-phase upload

- GIVEN a `PublicationAsset` with `mediaType: IMAGE/JPEG` and binary content available from storage
- WHEN `RealLinkedInAssetUploader.uploadAsset()` is invoked
- THEN the uploader MUST first call `POST /assets` to register the asset with LinkedIn
- AND receive a `digitalmediaAsset` URN and upload URL in the response
- AND then stream the binary content to the upload URL via `PUT /assets/{assetUrn}`
- AND return `ProviderAssetRef(providerAssetId: "urn:li:digitalmediaAsset:image:...", mediaType: IMAGE/JPEG)`

#### Scenario: LinkedIn asset uploader handles registration failure

- GIVEN a `PublicationAsset` with valid content
- WHEN `POST /assets` returns a failure response
- THEN the uploader MUST throw a `ProviderUploadException`
- AND the asset status MUST be transitioned to `FAILED`
- AND the publication MUST NOT be blocked (retryable error)

### Requirement: FakeLinkedInAssetUploader for Testing

The system MUST provide a `FakeLinkedInAssetUploader` test double that simulates the LinkedIn asset upload flow without requiring real credentials.

The fake MUST generate a deterministic fake URN in the correct format and MUST support configurable success/failure behavior for test scenarios.

#### Scenario: FakeLinkedInAssetUploader returns fake URN on success

- GIVEN a `FakeLinkedInAssetUploader` configured with `failOnNextCall = false`
- WHEN `uploadAsset()` is called with any valid asset
- THEN the fake MUST return a `ProviderAssetRef` with `providerAssetId` matching `urn:li:digitalmediaAsset:image:fake-asset-{uuid}`
- AND the operation MUST complete without throwing

#### Scenario: FakeLinkedInAssetUploader can be configured to fail

- GIVEN a `FakeLinkedInAssetUploader` configured with `failOnNextCall = true`
- WHEN `uploadAsset()` is called
- THEN the fake MUST throw `ProviderUploadException`
- AND the test can verify failure handling behavior

### Requirement: PublicationAsset Repository Write Path

The system MUST extend `PublicationAssetRepository` with a `create()` method that persists new asset records.

The `create()` method MUST accept the asset fields and persist a record with an assigned `assetId` and `status: READY`. The method MUST follow existing repository patterns and event publishing conventions.

#### Scenario: Repository creates new asset record

- GIVEN a valid `CreateAssetCommand` with all required fields
- WHEN `PublicationAssetRepository.create()` is called
- THEN a new `PublicationAsset` record MUST be persisted in the database
- AND a storage event MUST be published for audit trail
- AND the returned record MUST contain the assigned `assetId`

### Requirement: ProviderAssetRef Model

The system MUST define a `ProviderAssetRef` data class to hold provider-specific asset references returned after successful upload.

The `ProviderAssetRef` data class MUST contain: `providerAssetId` (required — the provider's URN or ID), `mediaType` (required — the resolved media type), and `accessUrl` (optional — URL for accessing the uploaded asset).

#### Scenario: ProviderAssetRef captures LinkedIn URN

- GIVEN a successful LinkedIn asset upload
- WHEN `RealLinkedInAssetUploader` completes the upload
- THEN the returned `ProviderAssetRef` MUST contain `providerAssetId` as the full LinkedIn URN
- AND `mediaType` matching the resolved type
- AND `accessUrl` as `null` (LinkedIn URN is self-addressing)

### Requirement: RealLinkedInPublisher Integrates Asset Uploader

The `RealLinkedInPublisher` MUST process non-empty asset lists by calling the `AssetUploader` for each `UPLOADED` asset and transforming each result into a LinkedIn `contentEntities` entry. For `EXTERNAL_URL` assets, the publisher MUST use the presigned URL or direct URL in the `source` field. The publisher MUST embed all asset URNs or URLs in the post body using LinkedIn's `contentEntities` format.

#### Scenario: Publisher publishes post with uploaded image asset

- GIVEN a publication command with one `PublicationAsset` where `sourceType: UPLOADED`
- WHEN `RealLinkedInPublisher.publish()` is called
- THEN the publisher MUST call `AssetUploader.uploadAsset()` to register the asset with LinkedIn
- AND receive a `ProviderAssetRef` containing the LinkedIn URN
- AND embed the URN in `contentEntities` of the LinkedIn post body
- AND update the asset record with the `providerAssetRef`

#### Scenario: Publisher publishes post with external URL asset

- GIVEN a publication command with one `PublicationAsset` where `sourceType: EXTERNAL_URL` and a valid `externalUrl`
- WHEN `RealLinkedInPublisher.publish()` is called
- THEN the publisher MUST use the `externalUrl` directly in the LinkedIn `contentEntities` `source` field
- AND no `AssetUploader` call is required for this asset type

#### Scenario: Publisher publishes post with multiple assets

- GIVEN a publication command with up to 10 `PublicationAsset` records
- WHEN `RealLinkedInPublisher.publish()` is called
- THEN the publisher MUST process each asset in order
- AND call `AssetUploader` for each `UPLOADED` asset
- AND embed all resulting URNs in the LinkedIn `contentEntities` array
- AND the post body MUST contain all registered assets

#### Scenario: Publisher handles asset upload failure gracefully

- GIVEN a publication command with assets
- WHEN `AssetUploader.uploadAsset()` throws a `ProviderUploadException` for one asset
- THEN the system MUST mark that specific asset's status as `FAILED`
- AND the publication MUST NOT be blocked from publishing
- AND the remaining assets MUST continue processing
- AND the failed asset MUST be eligible for retry in a subsequent attempt

### Requirement: LinkedInCapabilityValidator Media Type and Size Validation

The validator MUST reject publications where any asset has a `mediaType` not in the LinkedIn-supported set: `IMAGE/JPEG`, `IMAGE/PNG`, `IMAGE/GIF`, `IMAGE/WEBP`, `VIDEO/MP4`. The validator MUST also reject publications where any asset's file size exceeds 10MB.

#### Scenario: Validator rejects unsupported media type

- GIVEN a publication targets a LinkedIn account with an asset having `mediaType: APPLICATION/PDF`
- WHEN the publication is validated for queueing
- THEN the system MUST reject the publication as invalid
- AND the rejection MUST indicate the unsupported media type

#### Scenario: Validator rejects asset exceeding size limit

- GIVEN a publication targets a LinkedIn account with an asset larger than 10MB
- WHEN the publication is validated for queueing
- THEN the system MUST reject the publication as invalid
- AND the rejection MUST indicate the size limit exceeded

### Requirement: PublicationAsset Status Lifecycle

An asset MUST transition from `READY` to `PROCESSING` when `AssetUploader` begins the upload. Upon successful upload completion, the asset MUST retain or return to `READY` status with the `providerAssetRef` populated. Upon upload failure, the asset MUST transition to `FAILED` status.

#### Scenario: Asset transitions to PROCESSING during upload

- GIVEN a `PublicationAsset` with `status: READY`
- WHEN `AssetUploader.uploadAsset()` is invoked
- THEN the asset status MUST be transitioned to `PROCESSING`
- AND the status update MUST be persisted before the upload begins

#### Scenario: Asset returns to READY after successful upload

- GIVEN a `PublicationAsset` with `status: PROCESSING`
- WHEN `AssetUploader.uploadAsset()` completes successfully
- THEN the asset status MUST be transitioned to `READY`
- AND the `providerAssetRef` field MUST be populated with the returned reference

#### Scenario: Asset transitions to FAILED after upload failure

- GIVEN a `PublicationAsset` with `status: PROCESSING`
- WHEN `AssetUploader.uploadAsset()` throws a `ProviderUploadException`
- THEN the asset status MUST be transitioned to `FAILED`
- AND the `providerAssetRef` field MUST remain `null`
- AND the failure MUST be recorded for retry eligibility

### Requirement: Simple Queue Execution with Future Queue Portability

The system MUST execute scheduled publishing through a simple durable queue model that remains
portable to stronger async infrastructure later.

The first implementation MUST use authoritative persisted job records and worker claim semantics
rather than in-memory timers alone. The queue model MUST support due-time polling, job claiming,
retry rescheduling, and terminal completion semantics. The publishing domain MUST depend on
repo-local job and provider-delivery ports so a later migration to external queue infrastructure MAY
happen without redefining publication semantics.

#### Scenario: Due job is claimed exactly once under authoritative job state

- GIVEN a due publication job is available for processing
- WHEN a worker claims that job through the supported queue mechanism
- THEN the system MUST transition the job into a claimed or processing state in authoritative
  persistence
- AND later workers MUST treat that claimed job as unavailable unless recovery rules make it
  eligible again

#### Scenario: Queue portability remains an infrastructure concern

- GIVEN the system currently uses persisted database-backed jobs for publishing
- WHEN future scaling requires external queue infrastructure
- THEN the publication lifecycle and delivery semantics MUST remain stable
- AND the migration MUST be achievable by replacing infrastructure adapters rather than redefining
  the core publishing model

### Requirement: Calendar Query Endpoint

The system MUST expose a `GET /api/publishing/publications/calendar` endpoint returning publications filtered by date range, status, channel, and timezone.

The endpoint MUST accept `from` and `to` (ISO-8601 Instant, required), `status` (comma-separated, optional), `socialAccountId` (optional), and `timezone` (IANA, optional, defaults to UTC). The response MUST include `publications[]` with conflict flags, `activity[]` with per-day counts and density levels, and `conflicts[]` with overlapping publication pairs.

#### Scenario: Calendar query returns filtered publications

- GIVEN a workspace has publications across multiple dates and statuses
- WHEN a GET request is made with `from=2026-06-01T00:00:00Z&to=2026-06-30T00:00:00Z&status=SCHEDULED,QUEUED&socialAccountId=acc_li_1`
- THEN the response MUST include only SCHEDULED and QUEUED publications for the LinkedIn account within June 2026
- AND the response MUST include `activity` entries grouped by date

#### Scenario: Empty range returns empty result set

- GIVEN a workspace has no publications in the requested range
- WHEN a GET request is made with a date range that has no publications
- THEN the response MUST return 200 with empty `publications[]`, `activity[]`, and `conflicts[]`

### Requirement: Activity Density Aggregation

The system MUST aggregate publication counts per day using the user's timezone for activity indicators.

The aggregation MUST group publications by calendar date in the requested IANA timezone and classify each day into density levels: 0 = `none`, 1–2 = `light`, 3–5 = `medium`, 6+ = `high`. Thresholds MUST be defined as constants in `ActivityThresholds`.

#### Scenario: Activity aggregation respects timezone boundary

- GIVEN publications scheduled at 2026-06-09T23:00:00Z and 2026-06-10T01:00:00Z
- WHEN `timezone=America/New_York` (UTC-4)
- THEN both publications MUST be counted on 2026-06-09 in the New York timezone

### Requirement: Conflict Detection Policy

The system MUST detect conflicting publications when two SCHEDULED or QUEUED publications for the same social account fall within a configurable conflict window (default 15 minutes).

The `ConflictDetectionPolicy` MUST group publications by `socialAccountId`, sort by `scheduledFor`, and flag adjacent pairs where the gap is less than the conflict window. DRAFT, FAILED, CANCELLED, and PUBLISHED statuses MUST be excluded from detection.

#### Scenario: Adjacent same-account publications within window are flagged

- GIVEN two SCHEDULED publications for account `acc_li_1` at 10:00 and 10:10
- WHEN the conflict detection policy runs with a 15-minute window
- THEN both publications MUST be flagged with `hasConflict: true`
- AND the conflict entry MUST list both publication IDs with reason `OVERLAPPING_SCHEDULE`

#### Scenario: Publications across different accounts do not conflict

- GIVEN two SCHEDULED publications at the same time for different social accounts
- WHEN the conflict detection policy runs
- THEN neither publication MUST be flagged as conflicting

### Requirement: Quick-Create Endpoint

The system MUST expose `POST /api/publishing/publications/quick-create` that maps to `CreatePublicationCommand` with `scheduleMode = SCHEDULED_AT` and empty assets.

The endpoint MUST accept `socialAccountId`, `title`, `bodyText`, `scheduledFor`, and `priority`. The response MUST return the existing `PublicationResult`.

#### Scenario: Quick-create creates a scheduled publication

- GIVEN a valid workspace and social account
- WHEN a POST request submits `socialAccountId`, `bodyText`, and `scheduledFor`
- THEN a publication MUST be created with `scheduleMode = SCHEDULED_AT` and `status = SCHEDULED`
- AND the response MUST contain the new publication ID and created publication data

### Requirement: PATCH Reschedule Endpoint

The system MUST expose `PATCH /api/publishing/publications/{id}/reschedule` alongside the existing `POST` reschedule route for drag-and-drop updates.

The endpoint MUST accept `scheduleMode`, `scheduledFor`, and `priority`. Only SCHEDULED and QUEUED publications MUST be reschedulable. The response MUST return the existing `PublicationResult`.

#### Scenario: Drag-drop reschedule updates publication time

- GIVEN a SCHEDULED publication with `scheduledFor` at Monday 10:00
- WHEN a PATCH request submits `{"scheduleMode": "SCHEDULED_AT", "scheduledFor": "2026-06-09T14:00:00Z"}`
- THEN the publication's `scheduledFor` MUST be updated to 14:00
- AND the response MUST reflect the new schedule
