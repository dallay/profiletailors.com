# Publishing Specification

## Purpose

Define workspace-scoped social publishing behavior for Profile Tailors. This specification
establishes the provider-neutral contracts for connected social accounts, publications, scheduling,
queue execution, retry handling, and provider delivery seams, with LinkedIn personal-profile
publishing as the first implemented provider slice.

## Requirements

### Requirement: Authenticated Create Reconciliation

After an authenticated create succeeds, the client MUST replace any optimistic publication identity and fields with the returned backend publication. The store MUST use the returned `publicationId`, `status`, `scheduleMode`, `scheduledFor`, `nextSlotAfter`, and `socialAccountId` as authoritative values and MUST NOT retain a synthetic local ID.

#### Scenario: Standard create adopts server truth

- GIVEN authenticated creation has an optimistic local publication
- WHEN the backend returns a successful `PublicationResult`
- THEN the store MUST identify the publication by the returned `publicationId`
- AND MUST store the returned status, schedule, and social-account fields

#### Scenario: Freshly created publication is edited

- GIVEN an authenticated create succeeded and the publication was reconciled
- WHEN the user reopens it and saves an edit
- THEN PATCH MUST target the returned backend `publicationId`
- AND a successful response MUST replace local state with server truth

#### Scenario: PATCH target is absent in the workspace

- GIVEN the current workspace has no publication matching the PATCH identifier
- WHEN an authenticated edit is submitted
- THEN the backend MUST return 404
- AND publications in every other workspace MUST remain unchanged

### Requirement: Reconciled Composer Edit State

The edit composer MUST initialize schedule controls from authoritative reconciled fields. For `NOW` and `NEXT_SLOT`, it MUST NOT prefill stale or invalid custom date/time values. Existing assets MUST remain hydrated, previewed, and preserved when media is untouched. The explicit PATCH asset semantics established by #223 MUST remain unchanged.

#### Scenario: NOW creation reopens without stale custom schedule

- GIVEN a created publication is reconciled with `scheduleMode = NOW`
- WHEN the edit composer opens
- THEN it MUST select NOW
- AND MUST NOT prefill a custom scheduled date/time from optimistic state

#### Scenario: NEXT_SLOT creation reopens without stale custom schedule

- GIVEN a created publication is reconciled with `scheduleMode = NEXT_SLOT`
- WHEN the edit composer opens
- THEN it MUST select NEXT_SLOT and use backend scheduling fields
- AND MUST NOT prefill invalid custom schedule data

#### Scenario: Untouched existing media is preserved

- GIVEN a reconciled publication has resolvable existing assets
- WHEN the user edits non-media fields and saves
- THEN the assets MUST remain hydrated and previewed
- AND PATCH MUST omit `assetIds`, preserving persisted assets

#### Scenario: Explicit media clear or replacement remains supported

- GIVEN the edit composer contains existing assets
- WHEN the user explicitly clears all assets or selects replacements
- THEN PATCH MUST send `assetIds: []` for clear or the exact selected IDs for replacement

### Requirement: Workspace-Scoped Social Connections

The system MUST allow an authenticated workspace member to register and manage a social-provider
connection in workspace scope.

A social connection MUST be associated with exactly one workspace and one provider account identity.
The system MUST persist enough provider metadata to identify the connected account, provider type,
connection status, and credential freshness. Provider credential secrets MUST remain an
infrastructure concern and MUST NOT leak into public API responses. LinkedIn personal-profile
connection support MUST be implemented in this change. LinkedIn page support MAY be added later
without redefining the core connection model. Reconnecting the same provider account MUST use upsert
semantics to avoid uniqueness violations.

(Previously: Reconnect/upsert semantics were not specified; plain INSERT risked unique-constraint
violations.)

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

#### Scenario: Reconnecting the same LinkedIn profile is idempotent

- GIVEN a workspace already has an active LinkedIn personal profile connection
- WHEN the same LinkedIn profile is reconnected through OAuth
- THEN the system MUST update the existing connection and account records
- AND MUST NOT create duplicate records
- AND connection status MUST be `ACTIVE` with refreshed metadata

### Requirement: LinkedIn Completion Persists Connection and Account Atomically

The system MUST persist LinkedIn OAuth completion state atomically when finalizing a workspace social connection. The social connection write and social account write SHALL commit together or roll back together. The system MUST publish channel events only after the transaction commits successfully.

#### Scenario: LinkedIn completion commits both records

- GIVEN a valid authenticated workspace and successful LinkedIn OAuth completion data
- WHEN the backend finalizes the LinkedIn connection
- THEN the social connection MUST be persisted
- AND the social account MUST be persisted for the same workspace and provider account
- AND a channel event MAY be published after successful persistence

#### Scenario: Social account failure rolls back social connection

- GIVEN LinkedIn completion starts persisting a social connection and social account
- AND the social account persistence fails before transaction commit
- WHEN the completion handler returns an error
- THEN the social connection MUST NOT remain persisted
- AND the social account MUST NOT remain persisted
- AND no channel event MUST be published

#### Scenario: Event publishing is after transaction success

- GIVEN LinkedIn completion persistence succeeds inside a transaction
- WHEN the transaction commits successfully
- THEN the system MAY publish the channel-connected event
- AND event publication MUST NOT be required for the transaction to commit

### Requirement: Email Verification Required for Publishing and Social Connection

The system MUST require `emailStatus = VERIFIED` before a user can publish content or connect a social account.

This verification gate MUST apply consistently across immediate publishing, scheduled publishing requests, and social connection initiation or completion flows.

> **TODO:** Gate implementations for publishing and social-connection flows are deferred. Currently only `UPLOAD_MEDIA` (media library upload) enforces `emailStatus = VERIFIED`. The publishing handler, scheduling handler, and social connection initiation/completion handlers must be updated in a follow-up change to reject requests when `emailStatus != VERIFIED`. The `EmailVerificationPolicy` enum in the identity context should be extended with publishing and social-connection features, and the corresponding handlers should gate on those policies.

#### Scenario: Unverified user cannot publish

- GIVEN an authenticated user with `emailStatus = UNVERIFIED`
- WHEN the user attempts to create, queue, or publish content
- THEN the system MUST deny the request
- AND the denial MUST indicate email verification is required

#### Scenario: Unverified user cannot connect a social account

- GIVEN an authenticated user with `emailStatus = UNVERIFIED`
- WHEN the user attempts to initiate or complete a social connection flow
- THEN the system MUST deny the request
- AND the denial MUST indicate email verification is required

#### Scenario: Verified user can use gated publishing capabilities

- GIVEN an authenticated user with `emailStatus = VERIFIED`
- WHEN the user attempts to publish or connect a social account with otherwise valid input
- THEN the system MUST evaluate the request under normal publishing rules

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

The system MUST allow editing, deletion, and cancellation before a publication is claimed for
delivery.

A publication in `DRAFT`, `QUEUED`, or `SCHEDULED` MAY be edited, including text, media references,
schedule mode, and schedule timing, as long as the delivery job has not been claimed for processing.
Such a publication MAY also be cancelled or deleted before claim. Scheduler edit flows MUST persist
through the existing `PATCH /api/publishing/publications/{publicationId}` contract, and successful
responses MUST reflect server truth rather than local-only optimistic state. Publication writes MUST
target exactly one row in the caller's current workspace. A write scoped by `publicationId` MUST
update an existing publication only when both the publication identifier and workspace match the
current workspace context. If no publication row in the current workspace matches the requested
write target, the system MUST either create the draft in the current workspace when the operation is
a create/save flow, or reject the operation as not found for the current workspace when the
operation requires updating an existing publication. The system MUST NOT mutate a publication row
that belongs to another workspace. Once processing has begun, the system MUST prevent unsafe edits
or deletion that would invalidate the claimed delivery attempt.

(Previously: Pre-delivery publications were editable before claim, but the spec did not require
workspace-scoped write targeting or define behavior when an update target is missing in the current
workspace.)

#### Scenario: Queued publication is edited before claim

- GIVEN a publication is queued and not yet claimed by a worker
- WHEN the user edits the text or scheduling data
- THEN the system MUST persist the new publication content and delivery metadata
- AND the previous unclaimed job representation MUST no longer be treated as authoritative

#### Scenario: Scheduled publication edit uses backend response

- GIVEN the scheduler shows a publication in status `SCHEDULED`
- WHEN the user saves changes from the edit flow
- THEN the client MUST update its state from the successful PATCH response
- AND failed PATCH requests MUST surface an error without pretending the edit succeeded

#### Scenario: Same-workspace write updates the intended publication row

- GIVEN workspace A already owns publication `P1` in an editable pre-delivery state
- WHEN workspace A saves edits for publication `P1`
- THEN the system MUST update the existing row for workspace A
- AND it MUST NOT create a duplicate row for workspace A

#### Scenario: Save flow creates a draft when no current-workspace row exists

- GIVEN workspace A has no publication row with identifier `P1`
- WHEN workspace A performs a draft save that is allowed to create
- THEN the system MUST persist a new draft in workspace A
- AND the write MUST NOT depend on rows from other workspaces

#### Scenario: Cross-workspace publication rows remain isolated during writes

- GIVEN workspace A owns publication `P1`
- AND workspace B also has a row that is the only existing match for publication `P1` outside workspace A's scope
- WHEN workspace A performs a write for publication `P1`
- THEN the system MUST NOT update workspace B's row
- AND any persisted change MUST apply only within workspace A's scope

#### Scenario: Update fails when the current workspace cannot target a row

- GIVEN workspace A requests an update-only write for publication `P1`
- AND workspace A has no matching publication row for `P1`
- WHEN the system evaluates the write target
- THEN the system MUST reject the operation as not found for the current workspace
- AND it MUST leave rows in other workspaces unchanged

#### Scenario: Processing publication cannot be cancelled retroactively

- GIVEN a worker has already claimed a publication job for delivery
- WHEN the user attempts to cancel the publication
- THEN the system MUST reject cancellation for that in-flight attempt
- AND it MUST preserve deterministic processing semantics

### Requirement: Unpublished Publication Deletion API

The system MUST expose `DELETE /api/publishing/publications/{publicationId}` for unpublished
publications. The endpoint MUST permanently remove the publication and any unclaimed scheduling/job
linkage when the publication status is `DRAFT`, `QUEUED`, or `SCHEDULED`. The endpoint MUST reject
deletion for any other status and MUST NOT report local-only success.

#### Scenario: Delete scheduled publication succeeds

- GIVEN a publication exists in status `SCHEDULED`
- WHEN an authorized workspace member calls `DELETE /api/publishing/publications/{publicationId}`
- THEN the system MUST remove the publication from authoritative persistence
- AND any unclaimed related job state MUST no longer be returned by scheduler queries

#### Scenario: Delete published publication is rejected

- GIVEN a publication exists in status `PUBLISHED`
- WHEN an authorized workspace member calls the delete endpoint
- THEN the system MUST reject the request as not allowed for that status
- AND the publication MUST remain unchanged

### Requirement: Publication Edit/Delete Status Matrix

The system MUST use one pre-delivery policy for scheduler actions across frontend and backend.

| Status       | Edit        | Delete      |
|--------------|-------------|-------------|
| `DRAFT`      | MUST allow  | MUST allow  |
| `QUEUED`     | MUST allow  | MUST allow  |
| `SCHEDULED`  | MUST allow  | MUST allow  |
| `PROCESSING` | MUST reject | MUST reject |
| `PUBLISHED`  | MUST reject | MUST reject |
| `BLOCKED`    | MUST reject | MUST reject |
| `FAILED`     | MUST reject | MUST reject |
| `CANCELLED`  | MUST reject | MUST reject |

The frontend MUST hide or disable edit/delete actions for disallowed statuses, and the backend MUST
still enforce the same policy if a request is sent.

#### Scenario: Allowed status exposes action

- GIVEN the scheduler shows a publication in status `QUEUED`
- WHEN the user opens publication actions
- THEN edit and delete actions MUST be available
- AND invoking them MUST use backend APIs as the source of truth

#### Scenario: Disallowed status stays server-enforced

- GIVEN a publication is in status `PROCESSING`
- WHEN a client sends edit or delete anyway
- THEN the backend MUST reject the request
- AND the scheduler MUST preserve current server state after refresh or rollback

### Requirement: Composer-Based Publication Editing

The scheduler MUST use the full composer edit flow for editable unpublished publications instead of
inline detail-modal editing.

When a user opens an editable publication from the scheduler and chooses to edit it, the system MUST
close the read-only detail modal and reopen the full composer in edit mode with publication data
pre-filled from authoritative server-backed publication state. The composer edit flow MUST support
editing content, scheduling, priority, and media asset references while keeping the selected channel
read-only for the duration of the edit. The client MUST persist edits through the existing backend
PATCH publication contract, update local state from the successful backend response, and refresh the
scheduler view so the calendar reflects the saved server truth. In edit mode, the composer MUST hide
create-only affordances that imply a new publication is being created.

#### Scenario: User edits a scheduled publication from the scheduler

- GIVEN a user is viewing a scheduler publication in status `DRAFT`, `QUEUED`, or `SCHEDULED`
- WHEN the user selects the edit action
- THEN the detail modal MUST close
- AND the full composer MUST open in edit mode
- AND the composer MUST pre-fill content, schedule date/time, schedule mode, priority, attached
  media asset references, and the existing channel selection
- AND the existing channel selection MUST be read-only

#### Scenario: Published publications remain read-only in the scheduler

- GIVEN a user is viewing a scheduler publication in status `PUBLISHED`
- WHEN the scheduler renders publication actions
- THEN the edit action MUST NOT be rendered
- AND the publication details MUST remain read-only

#### Scenario: Saving composer edits uses backend response and refreshes the scheduler

- GIVEN the full composer is open in edit mode for an editable publication
- WHEN the user saves valid changes
- THEN the client MUST call the backend publication update API
- AND the client MUST update its state from the successful backend response
- AND the composer MUST close after the update succeeds
- AND the scheduler calendar MUST refresh to reflect the saved publication state

#### Scenario: Edit mode keeps channel locked and hides create-only controls

- GIVEN the full composer is open in edit mode
- WHEN the user views scheduling controls
- THEN the current channel MUST be shown as pre-selected and disabled
- AND the user MUST NOT be able to switch channels
- AND the create-another control MUST NOT be rendered

### Requirement: Publication Asset PATCH Tri-State Semantics

The publishing API MUST preserve CREATE asset behavior while giving PATCH publication edits explicit tri-state `assetIds` semantics. For edit requests, absent or `null` `assetIds` MUST preserve the publication's current asset IDs, an empty array MUST clear all current assets, and a non-empty array MUST replace current assets exactly in request order. CREATE semantics MUST remain unchanged: absent/default `assetIds` creates no assets, and provided IDs are used. Workspace-scoped targeting, update-not-found behavior, and the existing #224/#225 edit hardening behavior MUST remain unchanged.

#### Scenario: PATCH assetIds absent preserves current assets

- GIVEN a same-workspace editable publication has asset IDs `[A, B]`
- WHEN PATCH edits text and omits `assetIds`
- THEN the persisted publication MUST keep asset IDs `[A, B]`

#### Scenario: PATCH assetIds null preserves current assets

- GIVEN a same-workspace editable publication has asset IDs `[A, B]`
- WHEN PATCH includes `"assetIds": null`
- THEN the persisted publication MUST keep asset IDs `[A, B]`

#### Scenario: PATCH assetIds empty clears assets

- GIVEN a same-workspace editable publication has asset IDs `[A, B]`
- WHEN PATCH includes `"assetIds": []`
- THEN the persisted publication MUST have no asset IDs

#### Scenario: PATCH assetIds list replaces exactly

- GIVEN a same-workspace editable publication has asset IDs `[A, B]`
- WHEN PATCH includes `"assetIds": ["C", "A"]`
- THEN the persisted publication MUST have asset IDs `[C, A]`

#### Scenario: CREATE asset behavior is unchanged

- GIVEN a valid create request omits `assetIds` or uses the default value
- WHEN the publication is created
- THEN the persisted publication MUST have no asset IDs
- AND a create request with IDs MUST persist those IDs

#### Scenario: Workspace isolation remains enforced

- GIVEN workspace A edits publication `P1` and workspace B owns another `P1`
- WHEN A sends any PATCH `assetIds` shape
- THEN only A's target row MAY change
- AND #224/#225 status and workspace rules MUST remain unchanged

### Requirement: Composer Edit Asset Hydration and Submission

The scheduler composer MUST hydrate resolvable existing asset summaries when opened in edit mode and display previews for those assets. Missing or deleted assets MUST be handled gracefully without crashing the editor and MUST NOT silently clear unrelated valid asset IDs. Saving an edit without asset interaction MUST omit `assetIds` from PATCH. Explicit remove-all MUST send `assetIds: []`. Selecting or replacing assets MUST send the selected asset IDs exactly.

#### Scenario: Edit modal hydrates and previews existing assets

- GIVEN an editable publication has resolvable asset IDs `[A, B]`
- WHEN the edit modal opens
- THEN the composer MUST load summaries for A and B
- AND previews for A and B MUST be displayed

#### Scenario: Missing asset hydration is graceful

- GIVEN an editable publication references valid asset `A` and missing asset `Z`
- WHEN the edit modal hydrates assets
- THEN the editor MUST remain usable and show resolvable asset `A`
- AND it MUST NOT remove `A` or crash because `Z` is missing

#### Scenario: Untouched save omits assetIds

- GIVEN the edit modal opened with existing assets
- WHEN the user saves without touching assets
- THEN the PATCH body MUST omit `assetIds`

#### Scenario: Explicit remove-all sends empty array

- GIVEN the edit modal opened with existing assets
- WHEN the user removes all assets
- THEN the PATCH body MUST include `"assetIds": []`

#### Scenario: Selecting replacement sends selected IDs

- GIVEN the edit modal is open
- WHEN the user selects assets `[C, D]`
- THEN the PATCH body MUST include `"assetIds": ["C", "D"]`

#### Scenario: TDD acceptance coverage exists

- GIVEN backend and frontend regression tests are written first
- WHEN the focused suites run
- THEN they MUST cover all PATCH tri-state cases, CREATE compatibility, hydration, missing assets, untouched save omission, clear-all, replacement, and unchanged workspace/#224/#225 behavior

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

A workspace member MUST be able to submit a `CreateAssetCommand` containing `workspaceId`,
`storageKey` (for uploaded assets), `mediaType`, and optionally `externalUrl` (for external assets).
The system MUST validate the media type against LinkedIn-supported types and enforce the 10MB
per-asset size limit. Upon successful creation, the asset record MUST be persisted with status
`READY` and a unique `assetId`.

#### Scenario: Workspace member uploads a media asset

- GIVEN an authenticated workspace member with a connected LinkedIn account
- WHEN the member submits a `CreateAssetCommand` with `storageKey` pointing to a valid uploaded file
  and `mediaType: IMAGE/JPEG`
- THEN the system MUST persist a `PublicationAsset` record with `sourceType: UPLOADED`,
  `mediaType: IMAGE/JPEG`, `storageKey`, and `status: READY`
- AND the asset MUST be assigned a unique `assetId`

#### Scenario: Workspace member registers an external URL asset

- GIVEN an authenticated workspace member with a connected LinkedIn account
- WHEN the member submits a `CreateAssetCommand` with `externalUrl` and `mediaType: IMAGE/PNG`
- THEN the system MUST persist a `PublicationAsset` record with `sourceType: EXTERNAL_URL`,
  `mediaType: IMAGE/PNG`, `externalUrl`, and `status: READY`

#### Scenario: Asset creation rejects unsupported media type

- GIVEN an authenticated workspace member submits a `CreateAssetCommand` with
  `mediaType: APPLICATION/PDF`
- WHEN the system validates the media type against LinkedIn-supported types
- THEN the system MUST reject the request with a validation error
- AND no `PublicationAsset` record MUST be created

#### Scenario: Asset creation rejects file exceeding size limit

- GIVEN an authenticated workspace member submits a `CreateAssetCommand` with a `storageKey`
  referencing a file larger than 10MB
- WHEN the system validates the file size
- THEN the system MUST reject the request with a validation error
- AND no `PublicationAsset` record MUST be created

### Requirement: AssetUploader Port

The system MUST provide an `AssetUploader` port interface that provider adapters implement to
register and upload assets to their respective platforms.

The `AssetUploader` interface MUST declare:
`suspend fun uploadAsset(asset: PublicationAsset, content: Flow<ByteArray>): ProviderAssetRef`. The
port MUST be implemented by `RealLinkedInAssetUploader` for production and
`FakeLinkedInAssetUploader` for testing.

#### Scenario: AssetUploader implementation returns provider asset reference

- GIVEN a `PublicationAsset` with `sourceType: UPLOADED` and a valid `storageKey`
- WHEN `AssetUploader.uploadAsset()` is called with the asset and its binary content flow
- THEN the uploader MUST return a `ProviderAssetRef` containing the provider-specific
  `providerAssetId` (LinkedIn URN), `mediaType`, and optionally `accessUrl`
- AND the LinkedIn URN format MUST be `urn:li:digitalmediaAsset:{asset-type}:{asset-id}`

### Requirement: RealLinkedInAssetUploader Two-Phase Upload

The `RealLinkedInAssetUploader` MUST implement the LinkedIn asset upload flow in two distinct
phases.

**Phase 1 — Register**: The uploader MUST call `POST /assets` to register the asset and receive a
`digitalmediaAsset` URN plus an upload URL. **Phase 2 — Stream**: The uploader MUST `PUT` the binary
content directly to the upload URL obtained in Phase 1. The uploader MUST return a
`ProviderAssetRef` with the URN as `providerAssetId`.

#### Scenario: LinkedIn asset uploader completes two-phase upload

- GIVEN a `PublicationAsset` with `mediaType: IMAGE/JPEG` and binary content available from storage
- WHEN `RealLinkedInAssetUploader.uploadAsset()` is invoked
- THEN the uploader MUST first call `POST /assets` to register the asset with LinkedIn
- AND receive a `digitalmediaAsset` URN and upload URL in the response
- AND then stream the binary content to the upload URL via `PUT /assets/{assetUrn}`
- AND return
  `ProviderAssetRef(providerAssetId: "urn:li:digitalmediaAsset:image:...", mediaType: IMAGE/JPEG)`

#### Scenario: LinkedIn asset uploader handles registration failure

- GIVEN a `PublicationAsset` with valid content
- WHEN `POST /assets` returns a failure response
- THEN the uploader MUST throw a `ProviderUploadException`
- AND the asset status MUST be transitioned to `FAILED`
- AND the publication MUST NOT be blocked (retryable error)

### Requirement: FakeLinkedInAssetUploader for Testing

The system MUST provide a `FakeLinkedInAssetUploader` test double that simulates the LinkedIn asset
upload flow without requiring real credentials.

The fake MUST generate a deterministic fake URN in the correct format and MUST support configurable
success/failure behavior for test scenarios.

#### Scenario: FakeLinkedInAssetUploader returns fake URN on success

- GIVEN a `FakeLinkedInAssetUploader` configured with `failOnNextCall = false`
- WHEN `uploadAsset()` is called with any valid asset
- THEN the fake MUST return a `ProviderAssetRef` with `providerAssetId` matching
  `urn:li:digitalmediaAsset:image:fake-asset-{uuid}`
- AND the operation MUST complete without throwing

#### Scenario: FakeLinkedInAssetUploader can be configured to fail

- GIVEN a `FakeLinkedInAssetUploader` configured with `failOnNextCall = true`
- WHEN `uploadAsset()` is called
- THEN the fake MUST throw `ProviderUploadException`
- AND the test can verify failure handling behavior

### Requirement: PublicationAsset Repository Write Path

The system MUST extend `PublicationAssetRepository` with a `create()` method that persists new asset
records.

The `create()` method MUST accept the asset fields and persist a record with an assigned `assetId`
and `status: READY`. The method MUST follow existing repository patterns and event publishing
conventions.

#### Scenario: Repository creates new asset record

- GIVEN a valid `CreateAssetCommand` with all required fields
- WHEN `PublicationAssetRepository.create()` is called
- THEN a new `PublicationAsset` record MUST be persisted in the database
- AND a storage event MUST be published for audit trail
- AND the returned record MUST contain the assigned `assetId`

### Requirement: ProviderAssetRef Model

The system MUST define a `ProviderAssetRef` data class to hold provider-specific asset references
returned after successful upload.

The `ProviderAssetRef` data class MUST contain: `providerAssetId` (required — the provider's URN or
ID), `mediaType` (required — the resolved media type), and `accessUrl` (optional — URL for accessing
the uploaded asset).

#### Scenario: ProviderAssetRef captures LinkedIn URN

- GIVEN a successful LinkedIn asset upload
- WHEN `RealLinkedInAssetUploader` completes the upload
- THEN the returned `ProviderAssetRef` MUST contain `providerAssetId` as the full LinkedIn URN
- AND `mediaType` matching the resolved type
- AND `accessUrl` as `null` (LinkedIn URN is self-addressing)

### Requirement: RealLinkedInPublisher Integrates Asset Uploader

The `RealLinkedInPublisher` MUST process non-empty asset lists by calling the `AssetUploader` for
each `UPLOADED` asset and transforming each result into a LinkedIn `contentEntities` entry. For
`EXTERNAL_URL` assets, the publisher MUST use the presigned URL or direct URL in the `source` field.
The publisher MUST embed all asset URNs or URLs in the post body using LinkedIn's `contentEntities`
format.

#### Scenario: Publisher publishes post with uploaded image asset

- GIVEN a publication command with one `PublicationAsset` where `sourceType: UPLOADED`
- WHEN `RealLinkedInPublisher.publish()` is called
- THEN the publisher MUST call `AssetUploader.uploadAsset()` to register the asset with LinkedIn
- AND receive a `ProviderAssetRef` containing the LinkedIn URN
- AND embed the URN in `contentEntities` of the LinkedIn post body
- AND update the asset record with the `providerAssetRef`

#### Scenario: Publisher publishes post with external URL asset

- GIVEN a publication command with one `PublicationAsset` where `sourceType: EXTERNAL_URL` and a
  valid `externalUrl`
- WHEN `RealLinkedInPublisher.publish()` is called
- THEN the publisher MUST use the `externalUrl` directly in the LinkedIn `contentEntities` `source`
  field
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

The validator MUST reject publications where any asset has a `mediaType` not in the
LinkedIn-supported set: `IMAGE/JPEG`, `IMAGE/PNG`, `IMAGE/GIF`, `IMAGE/WEBP`, `VIDEO/MP4`. The
validator MUST also reject publications where any asset's file size exceeds 10MB.

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

An asset MUST transition from `READY` to `PROCESSING` when `AssetUploader` begins the upload. Upon
successful upload completion, the asset MUST retain or return to `READY` status with the
`providerAssetRef` populated. Upon upload failure, the asset MUST transition to `FAILED` status.

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

The system MUST expose a `GET /api/publishing/publications/calendar` endpoint returning publications
filtered by date range, status, channel, and timezone.

The endpoint MUST accept `from` and `to` (ISO-8601 Instant, required), `status` (comma-separated,
optional), `socialAccountId` (optional), and `timezone` (IANA, optional, defaults to UTC). The
response MUST include `publications[]` with conflict flags, `activity[]` with per-day counts and
density levels, and `conflicts[]` with overlapping publication pairs.

#### Scenario: Calendar query returns filtered publications

- GIVEN a workspace has publications across multiple dates and statuses
- WHEN a GET request is made with
  `from=2026-06-01T00:00:00Z&to=2026-06-30T00:00:00Z&status=SCHEDULED,QUEUED&socialAccountId=acc_li_1`
- THEN the response MUST include only SCHEDULED and QUEUED publications for the LinkedIn account
  within June 2026
- AND the response MUST include `activity` entries grouped by date

#### Scenario: Empty range returns empty result set

- GIVEN a workspace has no publications in the requested range
- WHEN a GET request is made with a date range that has no publications
- THEN the response MUST return 200 with empty `publications[]`, `activity[]`, and `conflicts[]`

### Requirement: Activity Density Aggregation

The system MUST aggregate publication counts per day using the user's timezone for activity
indicators.

The aggregation MUST group publications by calendar date in the requested IANA timezone and classify
each day into density levels: 0 = `none`, 1–2 = `light`, 3–5 = `medium`, 6+ = `high`. Thresholds
MUST be defined as constants in `ActivityThresholds`.

#### Scenario: Activity aggregation respects timezone boundary

- GIVEN publications scheduled at 2026-06-09T23:00:00Z and 2026-06-10T01:00:00Z
- WHEN `timezone=America/New_York` (UTC-4)
- THEN both publications MUST be counted on 2026-06-09 in the New York timezone

### Requirement: Conflict Detection Policy

The system MUST detect conflicting publications when two SCHEDULED or QUEUED publications for the
same social account fall within a configurable conflict window (default 15 minutes).

The `ConflictDetectionPolicy` MUST group publications by `socialAccountId`, sort by `scheduledFor`,
and flag adjacent pairs where the gap is less than the conflict window. DRAFT, FAILED, CANCELLED,
and PUBLISHED statuses MUST be excluded from detection.

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

The system MUST expose `POST /api/publishing/publications/quick-create` that maps to
`CreatePublicationCommand` with `scheduleMode = SCHEDULED_AT` and empty assets.

The endpoint MUST accept `socialAccountId`, `title`, `bodyText`, `scheduledFor`, and `priority`. The
response MUST return the existing `PublicationResult`.

#### Scenario: Quick-create creates a scheduled publication

- GIVEN a valid workspace and social account
- WHEN a POST request submits `socialAccountId`, `bodyText`, and `scheduledFor`
- THEN a publication MUST be created with `scheduleMode = SCHEDULED_AT` and `status = SCHEDULED`
- AND the response MUST contain the new publication ID and created publication data

### Requirement: PATCH Reschedule Endpoint

The system MUST expose `PATCH /api/publishing/publications/{id}/reschedule` alongside the existing
`POST` reschedule route for drag-and-drop updates.

The endpoint MUST accept `scheduleMode`, `scheduledFor`, and `priority`. Only SCHEDULED and QUEUED
publications MUST be reschedulable. The response MUST return the existing `PublicationResult`.

#### Scenario: Drag-drop reschedule updates publication time

- GIVEN a SCHEDULED publication with `scheduledFor` at Monday 10:00
- WHEN a PATCH request submits
  `{"scheduleMode": "SCHEDULED_AT", "scheduledFor": "2026-06-09T14:00:00Z"}`
- THEN the publication's `scheduledFor` MUST be updated to 14:00
- AND the response MUST reflect the new schedule

### Requirement: Idempotent Connection Upsert Semantics

Repository methods for persisting `SocialConnection` and `SocialAccount` MUST use ON CONFLICT
UPDATE (upsert) semantics. Reconnecting the same LinkedIn profile to the same workspace MUST update
the existing record rather than violate uniqueness constraints.

#### Scenario: Reconnecting the same LinkedIn profile updates existing connection

- GIVEN a workspace already has an active LinkedIn personal profile connection with a specific
  provider account ID
- WHEN the OAuth flow is completed again for the same LinkedIn profile and workspace
- THEN the system MUST update the existing `SocialConnection` and `SocialAccount` records
- AND connection status MUST be `ACTIVE` with refreshed credential reference and `connectedAt`
  timestamp
- AND no duplicate records MUST be created

#### Scenario: Reconnecting after revocation restores the connection

- GIVEN a LinkedIn connection has status `REVOKED`
- WHEN the same LinkedIn profile is reconnected in the same workspace
- THEN the system MUST update the existing record status to `ACTIVE`
- AND the credential reference MUST be refreshed

### Requirement: OAuth State Validation on Connection Completion

The existing `POST /api/publishing/linkedin/connections/complete` endpoint MUST validate the `state`
parameter before processing the connection. If `state` is absent, tampered, or expired, the endpoint
MUST reject the request.

#### Scenario: Completion with valid state succeeds

- GIVEN a valid `authorizationCode` and a `state` value that matches the signed original from
  initiation
- WHEN the completion endpoint is called
- THEN the system MUST process the LinkedIn OAuth exchange and persist the connection

#### Scenario: Completion with invalid state is rejected

- GIVEN a `state` value that does not match the signed original from initiation
- WHEN the completion endpoint is called
- THEN the system MUST return 400 with a state-validation error
- AND it MUST NOT exchange the authorization code or persist any connection

### Requirement: Frontend Channel Data Source Migration

The publishing Pinia store MUST replace mock channel seeding with backend-loaded channels. The store
MUST initialize `channels` as an empty array for authenticated users and load real channels from
`GET /api/publishing/channels`. Actions `fetchChannels()`, `connectLinkedInPersonalProfile()`, and
`completeLinkedInConnectionFromCallback()` MUST be added.

#### Scenario: Authenticated user loads channels from backend

- GIVEN the user is authenticated and the publishing store initializes
- WHEN `fetchChannels()` is called
- THEN it MUST call `GET /api/publishing/channels` via `apiFetch` with `X-Workspace-Id`
- AND populate `channels` with the backend response
- AND no mock channel data MUST be present

#### Scenario: Scheduling uses real backend account ID

- GIVEN the user selects a connected LinkedIn personal profile for scheduling
- WHEN the publication is submitted
- THEN the `socialAccountId` MUST be the real backend `socialAccountId` value
- AND it MUST NOT use `account-linkedin-mock` or any mock identifier

#### Scenario: Empty channel state shows Connect LinkedIn CTA

- GIVEN the user is authenticated and no channels are connected
- WHEN the publishing store loads with an empty channel list
- THEN the UI MUST display an empty state with a "Connect LinkedIn profile" call-to-action
- AND it MUST NOT display mock channels

### Requirement: LinkedIn Channel Avatar Support

The system MUST propagate an optional `avatarUrl` from LinkedIn OIDC userinfo through persistence,
APIs, store mapping, and UI rendering for connected LinkedIn channels.

A connected LinkedIn channel with a non-null `avatar_url` MUST display the avatar image in the
sidebar and channel selector. When the avatar URL is absent or fails to load, the system MUST render
the existing provider badge/initials fallback without layout shift. The `avatarUrl` field MUST be
additive and optional across all layers — domain, API, and frontend types. Provider secret values
MUST NOT be stored in `avatar_url`. Only HTTPS URLs SHALL be accepted; non-HTTPS values (including
data-URIs) MUST be rejected and the column left NULL.

#### Scenario: Connected LinkedIn channel with avatar shows profile picture

- GIVEN a workspace with an ACTIVE LinkedIn personal-profile social account that has a non-empty
  `avatar_url`
- WHEN the SPA requests `GET /api/publishing/channels`
- THEN the response MUST include `avatarUrl` for that channel
- AND the sidebar MUST render an `<img src={avatarUrl}>` for that channel

#### Scenario: Connected LinkedIn channel without avatar shows badge fallback

- GIVEN a workspace with an ACTIVE LinkedIn personal-profile social account where `avatar_url` is
  NULL
- WHEN channels are listed
- THEN the frontend MUST render the provider badge/initials fallback in place of an `<img>`

#### Scenario: Avatar URL broken or expired triggers fallback without layout shift

- GIVEN a channel with a non-empty `avatar_url` that 404s or otherwise fails to load in the browser
- WHEN the browser `<img>` element emits an `error` event
- THEN frontend code MUST replace the image with the provider badge/initials fallback
- AND this MUST NOT cause layout shift beyond the existing badge image size

#### Scenario: New LinkedIn connection persists avatar from userinfo.picture

- GIVEN a user completes LinkedIn OAuth and LinkedIn `userinfo` includes `picture`
- WHEN the backend finalizes the connection and persists social account metadata
- THEN `avatar_url` column on the `social_accounts` row MUST be populated with the safe `picture`
  value

#### Scenario: Reconnect updates avatar_url via upsert

- GIVEN an existing LinkedIn connection with previous `avatar_url`
- WHEN the user reconnects and LinkedIn `userinfo.picture` differs
- THEN the repository upsert semantics MUST update `avatar_url` to the new value

#### API Contract

`GET /api/publishing/channels` (200) response array items include optional field:

- `avatarUrl?: string | null` — absolute URL from LinkedIn userinfo.picture when present. MUST be
  present for channels whose persisted `avatar_url` is non-null. MUST NOT contain provider secrets.

API compatibility: The field is additive and optional; older clients MUST ignore unknown fields. New
clients MUST tolerate missing or null values.

#### Data Model

- Database migration: Add nullable column `avatar_url VARCHAR(1024) NULL` to `social_accounts`.
  Changeset MUST be additive and backward-compatible.
- Domain model `SocialAccount` MUST include `avatarUrl: String?`.
- Repository upsert semantics MUST set `avatar_url` when provided and leave existing value unchanged
  when absent during partial updates (unless reconnect flow explicitly provides new value).

Security: Do NOT store provider secret values in `avatar_url`. Validate that the value is an HTTPS
URL. If LinkedIn returns data-URI or non-HTTPS, sanitize or reject and leave column NULL.

#### Frontend

- Channel interface MUST include `avatarUrl?: string | null`. Mapper `apiChannelToChannel()` MUST
  read `avatarUrl` from API response.
- Sidebar and CreatePostModal MUST render
  `<img :src="channel.avatarUrl" @error="onAvatarError(channel)" v-if="channel.avatarUrl"/>`. When
  `avatarUrl` is null/absent or `avatarLoadFailed` is true, render provider badge/initials fallback
  with identical dimensions.
- Avatar image and badge MUST share same container size and border radius so swapping does not cause
  layout changes.
- Avatar images MUST provide `alt` text: `alt="{channel.displayName} avatar"`. Fallback badge MUST
  expose accessible label for assistive technologies.

#### Backend

- LinkedIn connector: When completing connection or during sync, read `picture` from LinkedIn OIDC
  userinfo `/v2/userinfo` if present. Validate that `picture` is an HTTPS URL and not a data URI. If
  invalid, log at debug and do not persist.
- Service layer: When persisting social account metadata, set `avatar_url` when value is provided by
  connector. Upsert semantics MUST replace the column when connector supplies a new value.
- Repository: Add `avatar_url` handling in read/write mappings between DB rows and domain objects.
- Controller/API: Include `avatarUrl` in the channel summary DTO returned by
  `GET /api/publishing/channels` when `avatar_url` is non-null. Ensure DTO does not leak provider
  tokens or other secrets.

#### Observability

- Debug logs in LinkedIn connector when parsing `picture` and when sanitization rejects values.
- Counter metric `publishing.linkedin.avatar.persisted` incremented when avatarUrl is persisted.

#### Rollout

- Deploy DB migration before server code that writes `avatar_url` (deploy in same release window
  preferred). Because column is nullable and additive, older server versions are compatible.
- Feature rollout is safe by default; UI will show fallback if API does not provide `avatarUrl`.

---

## LinkedIn Integration Publication (Delta from archive/2026-06-16-linkedin-integration-publication)

### Requirement: LinkedIn Capability-Bundled Integration Model

The system MUST model LinkedIn publishing as a set of explicit capability bundles rather than as one
monolithic "LinkedIn connected" integration.

Each LinkedIn social account MUST declare the capabilities it is eligible to use, the OAuth scopes
granted for those capabilities, the resource kind the capability applies to, and whether the
capability is available, gated, unsupported, or disabled. Capability evaluation MUST be performed
before OAuth activation, before publication validation, and again immediately before worker
execution.

The initial capability matrix MUST include at least:

| Capability bundle                  | MVP status                                      | Required grant / verification                                                   | Notes                                                                                                        |
|------------------------------------|-------------------------------------------------|---------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| Personal profile text publishing   | Supported                                       | `w_member_social`; author `urn:li:person:{id}`                                  | Launch MVP.                                                                                                  |
| Personal profile image publishing  | Supported                                       | `w_member_social`; validated image upload workflow                              | Launch MVP after media endpoint workflow validation.                                                         |
| Organization/page text publishing  | Supported but gated                             | `w_organization_social`; organization URN; sufficient page role                 | Only enabled when LinkedIn app/product access and page role checks pass.                                     |
| Organization/page image publishing | Supported but gated                             | `w_organization_social`; sufficient page role; validated image workflow         | Same org gate as text plus media gate.                                                                       |
| Organization mentions              | Gated / non-MVP                                 | Organization search/resolution API, mention syntax validation                   | Requires organization search/resolution API, mention syntax validation, and organization-not-found handling. |
| Video publishing                   | Gated / non-MVP                                 | Endpoint-specific video upload, finalize, processing, and permission validation | MUST NOT be required for launch MVP.                                                                         |
| PDF/document publishing            | Gated / non-MVP                                 | Endpoint-specific document workflow and permission validation                   | MUST NOT be required for launch MVP.                                                                         |
| Carousel publishing                | Gated / non-MVP                                 | Explicit product strategy and endpoint-specific workflow validation             | Strategy not defined for MVP. MUST NOT be required for launch MVP.                                           |
| Comments / threads                 | Gated / unsupported for first implementation    | Explicit future product enablement and feed/comment scopes                      | MUST NOT be required for launch MVP.                                                                         |
| Analytics / insights               | Out of scope for this change unless later added | Analytics-specific scopes                                                       | This change may preserve extension points but MUST NOT require analytics delivery.                           |

#### Scenario: Unsupported capability is rejected before provider calls

- GIVEN a workspace has an active LinkedIn personal-profile account with text and image publishing
  capabilities only
- WHEN a publication requests LinkedIn video, PDF/document, carousel, comments, or another gated
  capability that is not enabled
- THEN the system MUST reject or block the publication before calling LinkedIn
- AND the user-facing result MUST identify the capability as gated or unsupported rather than
  reporting a generic provider failure

#### Scenario: Organization page capability is evaluated independently

- GIVEN a user has connected a LinkedIn personal profile
- WHEN the user attempts to publish as an organization page
- THEN the system MUST require a separate organization/page account or capability grant
- AND MUST NOT infer organization publishing eligibility from personal publishing eligibility alone

#### Scenario: Organization mention is rejected when mention capability is gated

- GIVEN a LinkedIn publication includes an organization mention syntax
- AND the organization mentions capability is not enabled for the account and environment
- WHEN the publication is validated or executed
- THEN the system MUST reject or block the publication before calling LinkedIn
- AND the result MUST identify organization mentions as gated/non-MVP

### Requirement: LinkedIn Developer Portal Readiness

The system and operating runbook MUST define the external LinkedIn Developer Portal prerequisites
required before LinkedIn integration publication can be enabled for production users.

The readiness criteria MUST include LinkedIn app registration, Client ID/Client Secret provisioning
through secure configuration, an HTTPS absolute redirect URI registered exactly in LinkedIn
Developer Portal, an active company page association when organization/page publishing is enabled,
verification URL approval by a company page administrator, and required LinkedIn product access
review such as Community Management API access for organization/page management. Any product review
lead time MUST be tracked as a launch dependency, and organization/page publishing MUST remain gated
until the required access is approved.

#### Scenario: Production enablement is blocked until prerequisites are satisfied

- GIVEN LinkedIn publishing is configured for a production environment
- WHEN required LinkedIn app credentials, HTTPS redirect URI, page association, verification
  approval, or product access are missing
- THEN the system or deployment readiness process MUST treat real LinkedIn publishing as not
  launch-ready
- AND organization/page publishing MUST remain disabled until the required product access and page
  verification are complete

### Requirement: Modern Provider-Adapter Integration Approach

The LinkedIn integration MUST use modern Spring/Spring Boot-compatible OAuth2 and REST integration
patterns and MUST NOT use obsolete LinkedIn SDKs such as `spring-social-linkedin`.

LinkedIn provider communication MUST be isolated behind the existing publishing provider-adapter
boundary. In a WebFlux/R2DBC path, the implementation SHOULD use the existing reactive HTTP-client
approach such as `WebClient`. In a blocking Spring MVC path, the implementation SHOULD use Spring
Boot `RestClient`. The domain/application layer MUST NOT depend on the concrete HTTP client or
LinkedIn DTOs.

#### Scenario: Provider adapter owns LinkedIn HTTP details

- GIVEN the publishing worker dispatches a LinkedIn publication
- WHEN the system sends OAuth, media, or post requests to LinkedIn
- THEN the LinkedIn infrastructure adapter MUST perform the HTTP communication
- AND the provider-neutral publishing domain MUST remain independent of `WebClient`, `RestClient`,
  or LinkedIn payload details

### Requirement: LinkedIn Connection Status Semantics

LinkedIn connections MUST expose production-facing status semantics using first-class persisted
states `PENDING`, `ACTIVE`, `DISABLED`, `REQUIRES_RECONNECT`, `DELETED`, and `ERROR`.

The system MUST persist `DISABLED` and `REQUIRES_RECONNECT` as first-class states in the database.
`PENDING` and `DELETED` MAY be exposed through API mapping from existing internal states if full
migration is deferred.

The status semantics MUST be:

| State                |                       Publishable | Semantics                                                                                            |
|----------------------|----------------------------------:|------------------------------------------------------------------------------------------------------|
| `PENDING`            |                                No | OAuth or setup flow started but not fully validated/activated.                                       |
| `ACTIVE`             | Yes, subject to capability checks | Required credentials, scopes, and resource-role checks are currently valid.                          |
| `DISABLED`           |                                No | User or system has intentionally paused the account without deleting credentials/history.            |
| `REQUIRES_RECONNECT` |                                No | OAuth grant, token refresh, scope, or role state requires user reauthorization or permission repair. |
| `DELETED`            |                                No | Account was disconnected/soft-deleted and MUST NOT be used for new publications.                     |
| `ERROR`              |                     No by default | Non-reconnect operational error exists; recovery requires explicit classification before publishing. |

When a scheduled post targets a non-publishable LinkedIn account, the worker MUST NOT call LinkedIn
and MUST block the publication according to product rules with clear user guidance.

#### Scenario: Scheduled publication for reconnect-required account does not call LinkedIn

- GIVEN a scheduled publication targets a LinkedIn account whose status is `REQUIRES_RECONNECT`
- WHEN the publication becomes due
- THEN the worker MUST NOT call LinkedIn for that publication
- AND the publication MUST be marked `BLOCKED` with a reconnect-required reason
- AND a durable notification event MUST be recorded for the user-actionable reconnect requirement

#### Scenario: Disabled account is distinct from deleted account

- GIVEN a user disables a LinkedIn account without disconnecting it
- WHEN future publication jobs evaluate that account
- THEN the account MUST be treated as non-publishable with status `DISABLED`
- AND the system MUST preserve the distinction from `DELETED` for audit/history and possible
  re-enable behavior

### Requirement: LinkedIn Publication Lifecycle States

The system MUST define publication lifecycle states that include `BLOCKED` as a distinct state
from `FAILED`.

The publication lifecycle states MUST be:

| State        | Reversible | Semantics                                                                                                                                                                                                                                                                                                                                        |
|--------------|------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `DRAFT`      | Yes        | Publication is being composed, not yet submitted for scheduling.                                                                                                                                                                                                                                                                                 |
| `QUEUED`     | Yes        | Publication has been submitted and is awaiting scheduling.                                                                                                                                                                                                                                                                                       |
| `SCHEDULED`  | Yes        | Publication is scheduled for a future time.                                                                                                                                                                                                                                                                                                      |
| `PROCESSING` | No         | Publication execution is underway (validation, media upload, post creation).                                                                                                                                                                                                                                                                     |
| `PUBLISHED`  | No         | Publication succeeded with a remote LinkedIn post identifier.                                                                                                                                                                                                                                                                                    |
| `BLOCKED`    | Yes        | Publication cannot proceed due to non-publishable account status (DISABLED, REQUIRES_RECONNECT). When the account status is restored to ACTIVE, blocked publications MUST be automatically retried with exponential backoff (initial delay 1 minute, max delay 1 hour, max 5 retries). After max retries, the publication transitions to FAILED. |
| `FAILED`     | No         | Publication failed terminally due to an irrecoverable error, DELETED account, or max retries exhausted.                                                                                                                                                                                                                                          |
| `CANCELLED`  | No         | Publication was cancelled by the user or system before completion.                                                                                                                                                                                                                                                                               |

#### Scenario: Publication for DISABLED account is blocked and may retry on re-enable

- GIVEN a LinkedIn account is in `DISABLED` status
- AND there are scheduled or queued publications targeting that account
- WHEN the worker evaluates the publication for execution
- THEN the publication MUST be marked `BLOCKED` with a disabled-account reason
- AND a durable notification event MUST be recorded
- AND when the account status is restored to `ACTIVE`, blocked publications MUST be automatically
  retried using exponential backoff (initial delay 1 minute, max delay 1 hour, max 5 retries)
- AND after max retries the publication MUST transition to `FAILED`

#### Scenario: Publication for DELETED account fails terminally

- GIVEN a LinkedIn account is in `DELETED` status
- AND there are scheduled or queued publications targeting that account
- WHEN the worker evaluates the publication for execution
- THEN the publication MUST be marked `FAILED` with a deleted-account reason
- AND the system MUST NOT automatically retry the publication
- AND a durable notification event MUST be recorded

#### Scenario: BLOCKED publications auto-retry with exponential backoff

- GIVEN a publication is marked `BLOCKED` due to a DISABLED or REQUIRES_RECONNECT account
- WHEN the account status is restored to `ACTIVE`
- THEN the system MUST automatically retry blocked publications using exponential backoff
- AND the initial delay MUST be 1 minute, with max delay of 1 hour
- AND a maximum of 5 retries is allowed
- AND after max retries the publication MUST transition to `FAILED`

### Requirement: LinkedIn OAuth Scope Bundles and State Validation

The system MUST use LinkedIn 3-legged OAuth2 for delegated LinkedIn publishing connections and MUST
request scopes according to the selected capability bundle.

Personal-profile publishing MUST request and validate `w_member_social`. Organization/page
publishing MUST request and validate `w_organization_social` and MUST additionally verify that the
authenticated LinkedIn member has a page role sufficient to publish for the target organization.
OAuth callbacks MUST validate signed state, workspace, principal, provider, redirect URI, expiry,
and granted scopes before activating the connection.

If Spring Security OAuth2/OIDC client machinery is used for LinkedIn authorization, the
implementation MUST account for LinkedIn's OIDC nonce behavior by configuring a custom authorization
request resolver or equivalent customization that removes `nonce` and avoids nonce validation
failures. If the existing custom HMAC state plus direct token-exchange flow is retained, that flow
MUST remain the CSRF/state authority.

#### Scenario: Personal profile connection validates member publishing scope

- GIVEN an authenticated workspace member completes LinkedIn OAuth for a personal profile
- WHEN LinkedIn returns tokens and granted scopes
- THEN the system MUST activate the personal LinkedIn connection only if `w_member_social` was
  granted
- AND the system MUST reject activation when the signed OAuth state is invalid, expired, or
  mismatched

#### Scenario: Organization page connection validates scope and page role

- GIVEN an authenticated workspace member attempts to connect a LinkedIn organization page
- WHEN the OAuth grant includes `w_organization_social`
- THEN the system MUST verify that the member has a LinkedIn page role sufficient for posting to
  that organization
- AND the system MUST persist the page as a separate organization-page social account only after
  both scope and role checks succeed

#### Scenario: PENDING transitions to ACTIVE after successful OAuth completion

- GIVEN a LinkedIn account is in `PENDING` status after OAuth initiation
- WHEN the OAuth callback succeeds, signed state is valid, required scopes are granted, and scope
  validation passes
- THEN the system MUST transition the account status to `ACTIVE`
- AND the account MUST become eligible for publishing subject to capability checks

#### Scenario: PENDING transitions to ERROR on OAuth failure

- GIVEN a LinkedIn account is in `PENDING` status after OAuth initiation
- WHEN the OAuth callback fails, scopes are insufficient, or signed state validation fails
- THEN the system MUST transition the account status to `ERROR`
- AND the system MUST NOT activate the connection
- AND a durable notification event MUST be recorded identifying the failure reason

### Requirement: LinkedIn Token Lifecycle and Refresh-Aware Credential Resolution

The system MUST persist and use LinkedIn token lifecycle metadata for refresh, reconnect, and worker
credential decisions.

LinkedIn access-token expiry timestamps MUST be persisted, with the expected nominal lifetime around
60 days. Refresh-token absolute expiry timestamps MUST be persisted when LinkedIn provides refresh
tokens, with the expected nominal lifetime around 365 days. The system MUST NOT treat refresh-token
expiry as sliding; using a refresh token does not extend the original absolute refresh expiry unless
LinkedIn returns a replacement refresh token with new expiry metadata. Tokens MUST be encrypted at
rest, and public APIs MUST NOT expose token values.

The worker/publisher path MUST obtain provider credentials through a refresh-aware credential
resolver or equivalent port. The worker/publisher path MUST NOT read and use raw stored access
tokens directly. The resolver MUST check connection status, capability eligibility, access-token
expiry, refresh-token absolute expiry, refresh availability, and single-flight/concurrency
protection before returning an access token usable for a LinkedIn call.

The refresh-aware credential resolver MUST prevent concurrent refresh attempts for the same
credential using optimistic locking via a database version column. The credential record MUST
include a version column that is checked and incremented atomically during refresh. If a concurrent
refresh detects a version mismatch (OptimisticLockException), it MUST retry by re-reading the
credential record. If the re-read shows the token was already refreshed by another process, the
resolver MUST use the newly refreshed token instead of issuing a duplicate refresh.

The system SHOULD attempt access-token refresh ahead of access-token expiry when programmatic
refresh is available. The system MUST surface proactive reconnect UX before refresh-token absolute
expiry or when refresh is unavailable, revoked, or fails with a terminal provider error.

#### Scenario: Expiring access token refreshes automatically through resolver

- GIVEN a LinkedIn connection has an encrypted refresh token that has not reached absolute expiry
- AND the access token is expired or within the configured refresh-ahead window
- WHEN a scheduled or immediate publication needs provider access
- THEN the worker MUST ask the refresh-aware credential resolver for credentials
- AND the resolver MUST attempt token refresh according to policy before returning credentials or
  after a refreshable 401 classification
- AND on refresh success it MUST atomically persist the new encrypted access token, updated access
  expiry, and any replacement refresh token LinkedIn returns

#### Scenario: Expired refresh token requires reconnect

- GIVEN a LinkedIn connection's refresh-token absolute expiry has passed or refresh fails with a
  terminal `invalid_grant`/revocation equivalent
- WHEN the system attempts to publish or proactively refresh
- THEN the system MUST mark the connection as `REQUIRES_RECONNECT`
- AND the affected publication MUST be marked `BLOCKED` with a user-actionable reconnect reason
  rather than retrying indefinitely
- AND a durable notification event MUST be recorded

### Requirement: LinkedIn REST Posts API Contract

The LinkedIn publisher MUST use LinkedIn REST API semantics consistently for post creation.

The preferred post creation endpoint for this capability is `POST /rest/posts`. Requests to
`/rest/*` endpoints MUST include `Linkedin-Version` in `YYYYMM` format and
`X-Restli-Protocol-Version: 2.0.0`. The post author MUST be a LinkedIn URN: `urn:li:person:{id}` for
personal profiles or `urn:li:organization:{id}` for organization pages. Organic publication payloads
MUST include commentary, `visibility: PUBLIC`, `distribution.feedDistribution: MAIN_FEED`, and
`lifecycleState: PUBLISHED` unless a future capability explicitly defines another
visibility/distribution model.

The adapter MUST capture LinkedIn's created-post identifier from the authoritative response location
for the endpoint, such as `x-restli-id` for `/rest/posts`, and map provider errors into safe
internal error classes without exposing tokens or authorization codes.

#### Scenario: Personal text post uses person URN and required headers

- GIVEN an active LinkedIn personal-profile social account with valid `w_member_social` credentials
- WHEN the worker publishes a valid text-only post
- THEN the LinkedIn adapter MUST call `POST /rest/posts`
- AND the request MUST include `Linkedin-Version` and `X-Restli-Protocol-Version: 2.0.0`
- AND the request author MUST be `urn:li:person:{id}`
- AND the request MUST set public visibility, main-feed distribution, and published lifecycle state

#### Scenario: Organization text post uses organization URN

- GIVEN an active LinkedIn organization-page social account with valid `w_organization_social`
  credentials and verified publish role
- WHEN the worker publishes a valid text-only post
- THEN the LinkedIn adapter MUST call `POST /rest/posts`
- AND the request author MUST be `urn:li:organization:{id}`
- AND the local publication result MUST identify the remote LinkedIn post URN or ID returned by
  LinkedIn

### Requirement: LinkedIn Text and Commentary Validation

The system MUST validate LinkedIn text content before calling LinkedIn.

Text publication MUST enforce the configured LinkedIn commentary length limit, SHOULD use 3,000
characters as the default product limit unless a stricter configured limit applies, and MUST
sanitize editor HTML/Markdown into provider-safe plain text or LinkedIn-supported commentary syntax.
JSON serialization MUST be delegated to a JSON serializer rather than manual string concatenation.
LinkedIn commentary escaping, mention syntax, hashtag syntax, Unicode, newlines, apostrophes, and
backslashes MUST be handled as separate provider-text concerns from JSON escaping.

Organization mentions MAY be supported only when the system can resolve and preserve valid LinkedIn
mention syntax. Organization mentions are gated — the mention capability must be explicitly enabled.
Personal-profile mentions MUST remain gated unless a future approved capability defines the required
permissions and behavior.

#### Scenario: Invalid text is rejected locally

- GIVEN a LinkedIn publication contains commentary longer than the configured limit
- WHEN the user submits or schedules the publication
- THEN the system MUST reject the publication before calling LinkedIn
- AND the user-facing validation result MUST identify the length violation

#### Scenario: Commentary serialization preserves Unicode and apostrophes

- GIVEN a LinkedIn publication contains Unicode, emoji, apostrophes, newlines, backslashes, or
  supported mention syntax
- WHEN the adapter builds the provider payload
- THEN JSON serialization MUST produce a valid JSON request body
- AND LinkedIn commentary escaping MUST NOT double-escape content or corrupt supported LinkedIn
  syntax

### Requirement: LinkedIn Media Upload and Availability Flow

The system MUST model LinkedIn media publication as a multi-phase provider workflow before creating
the final post.

For MVP launch, media publishing MUST prioritize text plus image posts. Image posts MUST initialize
provider media through the relevant LinkedIn REST image endpoint such as `/rest/images`, capture the
returned upload URL and asset URN, PUT binary content to the upload URL, and wait until the provider
reports the resource as `AVAILABLE` when LinkedIn exposes status for that media type or apply a
documented conservative wait strategy when status is unavailable.

For MVP launch, image publishing MUST enforce a maximum of 10MB per asset and a maximum of 10 assets
per publication. These limits MUST be configurable.

The final `/rest/posts` request MUST reference the provider asset URN in the post content according
to the LinkedIn content type being published. Provider asset references, upload phase state,
availability status, retryability, and timeout/error classification MUST be durable enough to
support idempotent retries and audit.

Video, PDF/document, and carousel media workflows MUST remain gated/non-MVP until endpoint-specific
initialization, upload/finalize, availability polling, scope/product permission, timeout, and retry
semantics are validated and explicitly enabled.

#### Scenario: Image post waits for available asset before post creation

- GIVEN a publication contains a valid image asset within the 10MB size limit
- WHEN the worker prepares the LinkedIn publication
- THEN the adapter MUST initialize the image upload and persist the provider asset URN
- AND it MUST PUT the binary image to LinkedIn's upload URL
- AND it MUST wait for the image resource to become `AVAILABLE` when LinkedIn exposes status for
  that media type or use the configured conservative wait strategy
- AND only then create the LinkedIn post referencing the asset URN

#### Scenario: Gated video is not published in launch MVP

- GIVEN a publication contains a video asset
- AND the video capability is not explicitly enabled for the account and environment
- WHEN the publication is validated or executed
- THEN the system MUST reject or block the publication before video upload or post creation
- AND the result MUST identify video publishing as gated/non-MVP

#### Scenario: Document or carousel request is gated

- GIVEN a publication requests PDF/document or carousel behavior
- AND the corresponding capability is not explicitly enabled
- WHEN the publication is validated or executed
- THEN the system MUST reject or block the publication before calling LinkedIn
- AND carousel publishing strategy is not defined for MVP

### Requirement: Organization Page Gating and Role Verification

Organization/page LinkedIn publishing MUST be gated by app capability, OAuth scope, target
organization identity, and resource-level role verification.

The system MUST represent organization pages as separate social accounts or equivalent publish
targets from personal profiles. The organization account MUST store the LinkedIn organization
id/URN, display metadata allowed by privacy policy, granted scope bundle, validating member
principal, role verification status, and capability status. Page publishing MUST require
`w_organization_social` plus a LinkedIn page role sufficient for posting, such as the roles
documented by LinkedIn for the selected endpoint/product. If role verification fails, becomes stale,
or LinkedIn reports insufficient permission, the page account MUST become non-publishable and SHOULD
be marked `REQUIRES_RECONNECT` or `ERROR` according to the error cause.

#### Scenario: Page role loss blocks future jobs

- GIVEN an organization page account was previously active
- AND LinkedIn later reports that the validating member no longer has sufficient page role
- WHEN the worker classifies the provider error
- THEN the page account MUST become non-publishable
- AND future queued jobs for that page MUST NOT call LinkedIn until role/scope eligibility is
  restored
- AND a durable notification event MUST be recorded

#### Scenario: Invalid organization identifier is terminal

- GIVEN a user attempts to connect or publish to an invalid LinkedIn organization id or URN
- WHEN validation or LinkedIn classification identifies the organization as invalid
- THEN the operation MUST fail without an automatic retry loop
- AND the user-facing result MUST identify the organization target problem

### Requirement: LinkedIn Publication Result Persistence

Successful LinkedIn publication attempts MUST persist provider result metadata in first-class
fields.

When LinkedIn creates a post, the system MUST persist the remote LinkedIn post identifier or URN and
MUST persist `publicUrl` as a first-class nullable publication result field separate from external
id/URN. For MVP, `publicUrl` MUST remain null. LinkedIn does not return a public URL in the
`/rest/posts` response. The system MUST persist null and MUST NOT attempt URL derivation until a
documented, validated derivation strategy is defined in a future capability.

Delivery attempts MUST record phase, status, mapped provider error, retryability, correlation
metadata, LinkedIn endpoint/version, and remote identifiers when known, without storing secrets.

#### Scenario: Successful post stores remote id and nullable public URL

- GIVEN LinkedIn returns a successful post creation response with a remote post identifier
- WHEN the worker completes the attempt
- THEN the publication MUST be marked published
- AND the system MUST persist the remote identifier or URN
- AND the system MUST persist `publicUrl` as null for MVP
- AND `publicUrl` MUST remain null rather than being derived or fabricated until a validated
  derivation strategy is defined

#### Scenario: Result metadata excludes secrets

- GIVEN a LinkedIn attempt completes with success or failure
- WHEN audit/result metadata is persisted
- THEN the metadata MUST include safe correlation details and mapped outcome fields
- AND MUST NOT include access tokens, refresh tokens, authorization codes, client secrets, or raw
  provider payloads containing secrets

### Requirement: LinkedIn Publication List API

The system MUST provide a paginated list-publications endpoint that returns publication state,
remote identifier, public URL when available, failure reason, scheduled time, and account/provider
context.

The endpoint MUST support filtering by publication state, LinkedIn account, and date range. The
endpoint MUST return results in reverse chronological order by default.

#### Scenario: User reviews publication history with states and results

- GIVEN a workspace has LinkedIn publications in various states (PUBLISHED, BLOCKED, FAILED,
  DRAFT, QUEUED, SCHEDULED, PROCESSING, CANCELLED)
- WHEN the user requests the publication list
- THEN the system MUST return a paginated list with publication state, remote identifier, public
  URL (nullable), failure reason when applicable, scheduled time, and LinkedIn account context
- AND results MUST be ordered reverse chronologically by default

### Requirement: Durable Idempotency and Ambiguous Outcome Handling

LinkedIn publication idempotency MUST include durable operation and phase state. Blind retry after
an uncertain remote create is prohibited.

The system MUST persist an operation key or equivalent idempotency identity before making provider
create calls. The publication workflow MUST record durable phases such as validation, credential
resolution, media initialization, binary upload, media availability wait, post creation requested,
post creation succeeded, ambiguous/unknown outcome, failed terminally, and retry scheduled. At most
one worker execution MAY be in-flight for a given publication operation and target account.

The worker MUST NOT execute more than N concurrent publications for the same LinkedIn account,
where N is a configurable limit (default: 1).

If a LinkedIn post creation request times out, loses its response, or otherwise has an ambiguous
outcome after the request may have reached LinkedIn, the attempt MUST be marked ambiguous/unknown.
The next action MUST use durable attempt/idempotency state and a defined reconciliation strategy
when possible. If reconciliation is impossible because required read permissions or identifiers are
unavailable, the system MUST require operator-safe or user-safe resolution according to product
policy rather than issuing an unbounded duplicate create.

#### Scenario: Ambiguous timeout is not blindly replayed

- GIVEN a LinkedIn post creation request times out after the request may have reached LinkedIn
- WHEN the worker records the attempt outcome
- THEN the attempt MUST be marked ambiguous or unknown with the phase `post creation requested`
- AND the next action MUST use durable attempt/idempotency state and reconciliation policy
- AND the system MUST NOT blindly issue a duplicate create without resolving the ambiguous outcome
  according to policy

#### Scenario: Worker restart resumes from durable phase

- GIVEN a worker crashes after initializing image upload and before creating the post
- WHEN another worker resumes the publication
- THEN it MUST load the durable provider asset and phase state
- AND it MUST continue or retry from the safe phase according to retry classification rather than
  restarting the whole workflow blindly

#### Scenario: Concurrent publications for same account are serialized

- GIVEN multiple publications are queued for the same LinkedIn account
- AND the configurable concurrency limit N is set to 1 (default)
- WHEN the worker picks up publications for execution
- THEN it MUST NOT execute more than N publications concurrently for the same account
- AND additional publications for that account MUST wait until the in-progress publication completes
  or fails

### Requirement: Quota-Aware Error Handling and Retry

The LinkedIn provider integration MUST apply bounded, quota-aware error handling.

The system MUST rate-limit LinkedIn calls by provider endpoint and actor/account to reduce 429
responses and protect app/member quotas. Approximate quota assumptions MAY be configured, but the
system MUST allow operational tuning based on LinkedIn Developer Portal analytics and observed
response behavior. The system MUST treat 429, transient 5xx responses, and retryable write conflicts
as retryable with bounded backoff and jitter. The system MUST detect 401/credential failures,
attempt token refresh through the refresh-aware credential resolver when possible, and trigger
reconnect when refresh is impossible or expired.

The system MUST NOT retry indefinitely for validation errors, insufficient scopes, insufficient page
roles, invalid organization identifiers, unsupported/gated media, duplicate-content classifications
treated as terminal, or unrefreshable credentials.

#### Scenario: Rate limit response is retried with backoff

- GIVEN LinkedIn returns HTTP 429 for a publication or media request
- WHEN the retry budget has not been exhausted
- THEN the system MUST record the attempt as retryable
- AND schedule a later retry using bounded backoff and jitter
- AND preserve the same durable operation identity

#### Scenario: Insufficient page permission fails without retry loop

- GIVEN a publication targets a LinkedIn organization page
- AND LinkedIn indicates the member no longer has a role sufficient to publish
- WHEN the worker classifies the error
- THEN the system MUST mark the publication failed with a permission reason
- AND the social account MUST be marked non-publishable according to the chosen status mapping
- AND the system MUST NOT retry the same request indefinitely

### Requirement: Durable Notification Events

The system MUST record durable notification events for user-actionable LinkedIn publication and
connection outcomes.

Notification events MUST be persisted to a dedicated `notification_events` table with columns for
id, workspace_id, provider, social_account_id, publication_id, category, message, suggested_action,
public_url, and occurred_at. The table MUST be queryable by workspace, account, publication,
category, and date range.

A durable notification event MUST be recorded when a LinkedIn publication succeeds, fails
terminally, is blocked because the integration is disabled/non-publishable, requires reconnect,
lacks scopes or page roles, encounters media-processing failure/timeout, or enters an ambiguous
outcome requiring action. Delivery channel implementation MAY be separate or future-facing if no
notification subsystem exists, but the event record MUST be durable and sufficient for later
delivery.

Notification events MUST include provider, workspace/account context, publication identity when
applicable, result category, suggested user action when applicable, and `publicUrl` when a
successful publication has one. Notification events MUST NOT contain tokens or secrets.

#### Scenario: Reconnect-required event is recorded

- GIVEN a LinkedIn publication cannot proceed because the connection is `REQUIRES_RECONNECT`
- WHEN the worker blocks or fails the publication
- THEN the system MUST record a durable notification event
- AND the event MUST identify LinkedIn, the affected account, the publication when applicable, and
  the reconnect action

#### Scenario: Delivery channel can be future

- GIVEN the repository has no implemented notification delivery channel for this event type
- WHEN a LinkedIn notification event is recorded
- THEN the specification MUST still require durable event creation
- AND MUST NOT require a specific email, push, or in-app delivery implementation in this change
  phase

### Requirement: LinkedIn Privacy Retention

The system MUST minimize and expire LinkedIn-derived personal/profile and social activity data
according to product privacy constraints.

The system MUST NOT persist LinkedIn member profile data longer than 24 hours unless the field is
minimal durable connection metadata necessary to provide the user-requested integration, such as
provider account id, display name, account kind, avatar URL subject to existing sanitization rules,
granted scopes, capability status, and connection status. The system MUST NOT persist LinkedIn
social activity data longer than 48 hours unless a future approved analytics capability explicitly
changes retention. Retention cleanup MUST avoid deleting publication audit records required for
local publication history, but those records MUST contain only safe provider result/error metadata
and no unnecessary profile/social activity payloads.

Comments/threads and analytics are gated/non-MVP for this change; therefore any social activity data
collected only for comments, reactions, or analytics MUST either remain out of scope or comply with
the 48-hour retention limit until a future approved policy supersedes it.

#### Scenario: Short-lived profile payload is removed after retention window

- GIVEN the system temporarily stores LinkedIn profile payload data for connection completion or
  debugging
- WHEN the data is older than 24 hours
- THEN the retention process MUST delete or anonymize that temporary profile payload
- AND durable connection metadata required for the active integration MAY remain

#### Scenario: Social activity data is removed after retention window

- GIVEN the system stores LinkedIn social activity data for publication feedback, comments/status
  processing, or future analytics experiments
- WHEN the data is older than 48 hours and no future approved retention policy applies
- THEN the retention process MUST delete or anonymize the social activity data
- AND publication audit records MUST retain only safe minimal outcome metadata

### Requirement: LinkedIn Scheduler Frontend Changes

The scheduler UI at `/scheduler` MUST be updated to support LinkedIn-specific publishing flows as
part of this change.

#### Calendar Time Reference

The weekly calendar view MUST display a single time-axis column on the left side of the grid, NOT
duplicate time labels in every day column. Time labels MUST appear once per row (e.g., "6 AM",
"8 AM", etc.) on the left edge.

#### Monthly Calendar View

The scheduler MUST provide a monthly calendar view accessible from the existing view toggle
(Calendar/Week/Day). The monthly view MUST show publication posts as items on their scheduled
dates, with channel indicator and status color coding.

#### Channel Filtering for LinkedIn

When a user selects only the LinkedIn channel from the channel filter, the scheduler MUST display
only LinkedIn publications. The channel filter MUST support single-channel selection for LinkedIn,
and the "NEW POST" form MUST pre-select the LinkedIn channel when reached from a LinkedIn-filtered
view.

#### Publication Status Indicators

Post items in the calendar/list views MUST display status indicators using the publication
lifecycle states: DRAFT, QUEUED, SCHEDULED, PROCESSING, PUBLISHED, BLOCKED, FAILED, CANCELLED.
BLOCKED publications MUST be visually distinct from FAILED publications to indicate they may
auto-retry.

#### Reconnect UX

When a LinkedIn account is in `REQUIRES_RECONNECT` status, the scheduler MUST display a visible
reconnect prompt near the channel selector and on any BLOCKED publications targeting that account.
The reconnect prompt MUST link to the LinkedIn OAuth initiation flow.

#### Scenario: Monthly view shows scheduled LinkedIn posts

- GIVEN a workspace has LinkedIn publications scheduled for various dates in the current month
- WHEN the user switches to the monthly calendar view
- THEN the calendar MUST display publication items on their scheduled dates
- AND each item MUST show the channel indicator and status color

#### Scenario: Time axis is displayed once per row in weekly view

- GIVEN the user is in the weekly calendar view
- WHEN the calendar renders time slots
- THEN time labels MUST appear once on the left side of the grid
- AND MUST NOT be duplicated in each day column

#### Scenario: LinkedIn-only filter shows only LinkedIn publications

- GIVEN the user selects only the LinkedIn channel in the filter
- WHEN the scheduler renders the calendar or list view
- THEN only LinkedIn publications MUST be displayed
- AND the NEW POST form MUST pre-select LinkedIn as the channel

#### Scenario: BLOCKED publication shows reconnect prompt

- GIVEN a LinkedIn account is in `REQUIRES_RECONNECT` status
- AND there are BLOCKED publications targeting that account
- WHEN the user views the scheduler
- THEN a reconnect prompt MUST be visible near the channel selector
- AND each BLOCKED publication MUST show a reconnect action

---

## Centralized Media Library (Delta from archive/2026-06-19-centralized-media-library)

### Requirement: Publication Creation Uses Persisted Media Asset References

The publishing capability MUST allow workspace publications to reference persisted media-library
assets through `assetIds` instead of relying on local-only browser attachment state.

For this MVP, publication creation MUST accept previously uploaded workspace asset identifiers that
resolve to ready assets owned by the separate media-library bounded context. Publishing MUST consume
those identifiers through a provider-neutral media-library contract rather than by treating media as
an internal publishing concern. The system MUST continue using the current provider-neutral asset
reference pattern so downstream publishing integrations can consume stored assets through existing
storage-backed publishing flows. The client MUST submit real persisted `assetIds` when the user
attaches uploaded or previously stored media.

#### Scenario: Publication request attaches a previously uploaded workspace asset

- GIVEN a workspace has a persisted uploaded media asset that is `READY` for publication use
- WHEN the user creates a publication and selects that asset
- THEN the publication request MUST include the persisted asset identifier in `assetIds`
- AND the system MUST associate the publication with that asset through the
  `publication_asset_links` join record (publicationId, assetId). This join table already exists in
  the schema (see changelog `005-create-publication-asset-links.yaml`). Each publication may
  reference multiple assets. The assetId in the link record MUST resolve to a MediaAsset owned by
  the media bounded context.

#### Scenario: Publication created with no attached assets

- GIVEN a workspace member creates a publication with an empty `assetIds` list (text-only post)
- WHEN the system validates the publication
- THEN the system MUST accept the request without calling the media asset resolution port
- AND no media validation is performed for zero-asset publications
- AND the publishing handler MUST short-circuit before calling the media port when `assetIds` is
  empty, so zero-asset publications succeed even when the media context is unavailable.

#### Scenario: Local-only attachments are not the source of truth for publication submission

- GIVEN a user has selected media in the composer
- WHEN the user submits the publication
- THEN the client MUST use persisted media-library asset identifiers for attached media
- AND it MUST NOT submit the publication as if attachments were local-only temporary state

### Requirement: Publication Attachment Validation Uses Workspace Media Library State

The system MUST validate attached `assetIds` against workspace scope and media readiness through the
media-library bounded-context boundary before publication acceptance or execution.

Every attached asset MUST belong to the active workspace and MUST be in a state `READY` for
publishing consumption. The system MUST reject asset references that are missing, belong to another
workspace, or represent incomplete uploads. This validation MUST occur before downstream provider
publishing begins. The same validation rules MUST apply both when a publication is created and when
an existing publication is edited to change its attached assets. Publishing MUST treat media
readiness and asset existence as media-library concerns even if existing persistence structures are
reused behind the boundary.

#### Scenario: Publication rejects asset from another workspace

- GIVEN a publication request is made in workspace A
- AND one of the supplied `assetIds` belongs to workspace B
- WHEN the system validates the publication request
- THEN the system MUST reject the request before creating or executing the provider publication
- AND the response MUST identify that the asset is unavailable in the active workspace

#### Scenario: Publication rejects incomplete uploaded asset

- GIVEN a publication request includes an asset whose upload has not completed successfully
- WHEN the system validates the publication request
- THEN the system MUST reject or block the publication before provider execution
- AND the result MUST identify that the asset is not ready for publishing use

#### Scenario: Publication edit rejects incomplete uploaded asset

- GIVEN an existing publication is being edited in a workspace
- AND the updated request includes an asset whose upload has not completed successfully
- WHEN the system validates the edited publication request
- THEN the system MUST reject or block the update before provider execution
- AND the result MUST identify that the asset is not ready for publishing use

#### Scenario: Publication rejects missing asset identifier

- GIVEN a publication request includes an asset identifier that does not resolve within the active
  workspace
- WHEN the system validates the publication request
- THEN the system MUST reject the request before provider execution
- AND the result MUST identify that the asset is unavailable in the active workspace

#### Scenario: Provider dispatch handles storage unavailability after validation

- GIVEN an asset passed `READY` validation before provider dispatch
- WHEN the storage binary is unavailable at dispatch time (e.g., transient storage error)
- THEN the provider adapter MUST treat this as a retriable infrastructure error
- AND MUST NOT mark the publication as successfully published
- AND MUST surface the failure through the existing publication failure mechanism

#### Scenario: Publication request with duplicate asset identifiers

- GIVEN a publication request includes the same `assetId` value more than once in the `assetIds`
  list
- WHEN the system processes the request
- THEN the system MUST silently deduplicate the list before validation and link creation
- AND the publication MUST be associated with each unique asset only once

### Requirement: Composer Media Selection Uses Reusable Workspace Assets

The SPA composer MUST support selecting media from persisted workspace assets, including newly
uploaded assets, previously uploaded assets from the same workspace, and feature-flagged provider imports that resolve to persisted assets.

The composer flow MUST distinguish transient `pickerSelectionIds`, draft-level `draftAttachmentIds`, and persisted publication `assetIds`. Opening the picker MUST stage the current draft attachments for replace-set editing. Upload or provider import that creates or resolves persisted assets MUST refresh them into the active picker session and MUST auto-stage the resulting asset IDs once they resolve to selectable persisted assets. The draft MUST change only when the user explicitly applies the picker result. Publication submission MUST continue using persisted `assetIds` derived from the confirmed draft attachment set.
(Previously: The composer supported persisted asset reuse, but did not define staged picker selection, draft replacement semantics, or same-session upload/import auto-staging.)

#### Scenario: Upload or import stages persisted assets before draft commit

- GIVEN the picker is open in the composer
- WHEN upload or provider import yields persisted asset IDs
- THEN those asset IDs MUST become available in the active picker session
- AND newly created assets MUST become staged selections automatically once they resolve to selectable persisted assets
- AND the draft attachment set MUST remain unchanged until apply

#### Scenario: Applying the picker updates draft attachments but not publication persistence

- GIVEN staged `pickerSelectionIds` differ from `draftAttachmentIds`
- WHEN the user confirms the picker
- THEN `draftAttachmentIds` MUST be replaced by the staged selection
- AND persisted publication `assetIds` MUST change only when the draft is later saved or published

## ADDED Requirements

### Requirement: Multi-channel attachment limit enforcement

The composer and publishing flow MUST enforce an effective attachment limit equal to the minimum `maxAttachments` across all currently selected target channels. If a later channel change makes the current draft attachments invalid, the system MUST preserve the attachments in the draft, surface the invalid state, and block publish or schedule actions until the author resolves the mismatch.

#### Scenario: Effective limit uses the strictest selected channel

- GIVEN the author selects multiple target channels with different attachment limits
- WHEN the composer evaluates attachment capacity
- THEN the effective limit MUST equal the minimum channel `maxAttachments`
- AND the picker or draft flow MUST prevent confirming more attachments than that limit

#### Scenario: Channel change invalidates existing attachments without auto-removal

- GIVEN the draft currently has attachments within the prior limit
- WHEN the selected channels change and the effective limit becomes lower than the current attachment count
- THEN the system MUST keep the existing draft attachments
- AND it MUST surface an invalid state and block publish or schedule until resolved

### Requirement: Existing Publishing Consumers Continue Using Storage-Backed Assets

Existing publishing integrations that already consume storage-backed asset references MUST remain
compatible with media-library-backed `assetIds`.

This change SHALL preserve the existing publishing path in which provider adapters resolve uploaded
assets from platform-managed storage using persisted asset metadata. The media-library MVP MUST NOT
require a broader publishing-domain refactor outside the current asset reference flow, but it MUST
keep media ownership in the separate media-library bounded context. For the current product MVP,
LinkedIn is the required downstream consumer that MUST remain compatible. If other providers are
active in the codebase, they MUST either continue working with the same provider-neutral asset
reference contract or remain explicitly out of scope for this change.

#### Scenario: LinkedIn publishing consumes media-library-backed asset reference

- GIVEN a publication references a persisted uploaded asset through `assetIds`
- WHEN the existing LinkedIn publishing flow resolves publication assets for provider upload
- THEN it MUST consume the stored asset through the existing storage-backed asset path
- AND the media library MUST behave as the source of truth for that asset reference rather than an
  internal publishing-owned media store

#### Scenario: End-to-end persisted media flow reaches provider publishing through existing asset path

- GIVEN a workspace member creates a supported media asset, completes upload successfully, browses
  or reuses that `READY` asset, and submits a publication with its persisted `assetId`
- WHEN the publication proceeds through the existing provider publishing flow
- THEN the system MUST validate that the asset is `READY` and workspace-scoped before execution
- AND the downstream provider integration MUST resolve the stored binary through the existing
  storage-backed asset path

### Requirement: Publication Edit Hardening Quality Gates

The publication editing flow MUST remain protected by backend quality gates plus dedicated unit and
end-to-end regression coverage.

Hardening changes to publication editing MUST preserve existing persistence contracts while keeping
the backend publishing quality gate green. The composer edit-mode behavior MUST remain covered by
focused unit tests for prefill, locked channel state, create-only control hiding, update submission
branching, success emission, and surfaced error handling. The scheduler edit journey MUST remain
covered by end-to-end browser tests that verify edit entry from the detail modal, composer prefill
behavior, successful save, and scheduler refresh after the update.

#### Scenario: Backend publishing quality gate passes after persistence hardening

- GIVEN the publishing persistence adapter is refactored to satisfy code-quality constraints
- WHEN backend quality checks are run
- THEN the publishing behavior MUST remain unchanged
- AND the backend quality gate MUST pass without `LargeClass` or `LongMethod` failures in
  `R2dbcPublishingRepositories.kt`

#### Scenario: Composer edit mode has focused regression coverage

- GIVEN `CreatePostModal` opens in edit mode for an existing publication
- WHEN the unit test suite runs
- THEN dedicated tests MUST verify prefill of content, scheduling, priority, media, and locked
  channel state
- AND dedicated tests MUST verify that edit mode calls `updatePost()` rather than create-mode
  submission
- AND dedicated tests MUST verify that create-only controls are hidden and update errors are
  surfaced

#### Scenario: Scheduler publication edit flow is protected end-to-end

- GIVEN a user opens an editable unpublished publication from the scheduler
- WHEN the user enters edit mode, updates the publication, and saves
- THEN end-to-end coverage MUST verify that the detail modal closes
- AND the composer MUST open in edit mode with pre-filled values
- AND the update MUST succeed through the existing edit flow
- AND the scheduler MUST refresh to show the saved state

### Note on Legacy Publishing Asset Records

Existing `publication_assets` rows created before this change (legacy rows) may lack the
media-bounded-context ownership semantics introduced here. For the MVP transition:

1. Legacy rows that have status `READY` and a valid `storageKey` MUST continue to be accepted by the
   publishing validation flow.
2. Legacy rows that are missing required fields or have non-`READY` status MUST be treated as
   invalid by publication validation.
3. No backfill migration is required for MVP; legacy rows are treated as-is at the publishing
   boundary.

---

## Publishing Mutation Transactions (Delta from archive/2026-06-28-2026-06-28-issue-191-publishing-transactions)

### Requirement: Atomic Publication and Job Mutations

Create, Edit, Cancel, Retry, and Reschedule MUST execute their publication mutation (including
asset-link changes) and corresponding job mutation through one `AtomicTransactionRunner` boundary.
Both sides MUST commit together or both MUST roll back.

#### Scenario: Paired mutation commits

- GIVEN any of the five workflows has valid inputs
- WHEN both publication and job mutations succeed
- THEN the persisted publication and asset links MUST reflect the result
- AND the corresponding job mutation MUST be committed

#### Scenario: Create job mutation fails

- GIVEN no publication, asset links, or job exists for a Create request
- WHEN publication persistence succeeds but job persistence fails
- THEN the publication, its asset links, and its job MUST NOT exist

#### Scenario: Existing workflow job mutation fails

- GIVEN Edit, Cancel, Retry, or Reschedule targets a persisted publication with its current asset
  links and job
- WHEN the publication mutation succeeds but the job mutation fails
- THEN the prior publication state and asset links MUST remain unchanged
- AND any pre-existing job MUST remain unchanged

### Requirement: Framework-Neutral Transaction Orchestration

The five handlers MUST depend on `AtomicTransactionRunner` and MUST NOT depend on Spring, Reactor,
or coroutine-Reactor transaction APIs. Authorization, lifecycle and capability validation, external
reads, and media resolution MUST complete before the transaction starts; only paired persistence
mutations SHALL run inside it.

#### Scenario: Validation fails before transaction

- GIVEN a mutation request fails authorization, lifecycle, or capability validation
- WHEN the handler processes the request
- THEN `AtomicTransactionRunner` MUST NOT be invoked
- AND no publication or job write MUST occur

#### Scenario: External read fails before transaction

- GIVEN a required account, publication, asset, or media-resolution read fails
- WHEN the handler prepares the mutation
- THEN no transaction MUST begin
- AND no durable mutation MUST occur

### Requirement: Jobs Use Persisted Publication Result

Edit, Retry, and Reschedule MUST derive replacement jobs from the publication returned by
persistence, not from the pre-persistence draft.

#### Scenario: Persistence normalizes publication data

- GIVEN persistence returns publication identity, workspace, status, or schedule data different from
  the prepared draft
- WHEN Edit, Retry, or Reschedule creates its replacement job
- THEN the job MUST use the persisted publication result
- AND no stale pre-persistence value MAY determine the job

### Requirement: Delete Behavior Is Unchanged

Delete MUST retain its existing behavior and MUST NOT acquire `AtomicTransactionRunner` wiring as
part of this change.

#### Scenario: Delete executes existing path

- GIVEN a publication is eligible for deletion
- WHEN Delete is invoked
- THEN its existing persistence behavior MUST remain unchanged
- AND it MUST NOT invoke `AtomicTransactionRunner`

### Requirement: Update-Only Publication Misses Return HTTP 404

The system MUST translate current-workspace publication misses for update-only publishing operations into HTTP 404 at the HTTP boundary.

Any endpoint that intentionally scopes publication lookup by the caller's current workspace and throws `PublicationNotFoundException` for a miss MUST expose that miss as not found rather than an internal server error. This contract applies only to update-only operations and MUST NOT redefine create/save flows that are allowed to create a draft when no current-workspace row exists.

#### Scenario: Edit request misses the current-workspace publication

- GIVEN `PATCH /api/publishing/publications/{publicationId}` is an update-only operation
- AND the current workspace has no matching publication row for `publicationId`
- WHEN the HTTP request reaches the publishing boundary
- THEN the system MUST return HTTP 404
- AND the response MUST NOT degrade to HTTP 500

#### Scenario: Sibling update-only operations share the same not-found contract

- GIVEN delete, cancel, retry, or reschedule uses the same current-workspace publication lookup semantics
- AND the operation intentionally treats cross-workspace targets as not found
- WHEN no matching publication exists in the current workspace
- THEN the system MUST return HTTP 404 for that endpoint
- AND it MUST leave rows in other workspaces unchanged

#### Scenario: Create-capable save flows remain out of scope

- GIVEN a publishing flow is explicitly allowed to create a draft when the current workspace has no matching row
- WHEN that flow evaluates a missing current-workspace target
- THEN this requirement MUST NOT force HTTP 404
- AND the flow MUST continue to follow its create/save contract

## Friendly Publishing Errors (Delta from archive/2026-07-15-dallay-484-friendly-publishing-errors)

### Requirement: Safe Friendly Publishing Failure Presentation

Failed publication UI MUST show user-friendly problem labels, explanations, and recovery actions from an allowlisted product taxonomy. The UI MUST NOT render exception names, stack traces, package/class names, raw provider/storage responses, URLs, tokens, tenant/workspace/internal IDs, bucket/object paths, HTTP/client debug strings, or raw unknown codes/messages.

#### Scenario: Missing media shows replacement guidance

- GIVEN a failed publication has category `MEDIA_NOT_FOUND`
- WHEN the user opens publication details
- THEN the UI MUST show localized copy explaining the media could not be found
- AND it MUST suggest reattaching media or editing the post

#### Scenario: Temporarily unavailable media suggests retry

- GIVEN a failed publication has category `MEDIA_UNAVAILABLE`
- WHEN the user opens publication details
- THEN the UI MUST explain that media could not be accessed temporarily
- AND it MUST suggest retrying later or replacing the asset if the problem persists

#### Scenario: Account authorization expired asks reconnect

- GIVEN a blocked publication has category `ACCOUNT_RECONNECT_REQUIRED`
- WHEN the user opens publication details
- THEN the UI MUST show localized reconnect guidance
- AND it MUST NOT expose provider token, auth URL, or OAuth debug details

#### Scenario: Terminal account unavailability offers a safe alternative

- GIVEN a failed publication has category `ACCOUNT_UNAVAILABLE`
- WHEN the user opens publication details
- THEN the UI MUST explain that the selected account cannot publish
- AND it MUST suggest restoring or selecting an available account

#### Scenario: Transient service outage suggests retry later

- GIVEN a failed publication has category `PROVIDER_UNAVAILABLE` or `PROVIDER_RATE_LIMITED`
- WHEN the user opens publication details
- THEN the UI MUST explain the service is temporarily unavailable
- AND it MUST suggest retrying later or rescheduling

#### Scenario: Validation failure explains safe product reason

- GIVEN a failed publication has category `PROVIDER_VALIDATION_FAILED`
- WHEN the user opens publication details
- THEN the UI MUST show localized validation guidance
- AND it MUST avoid raw provider response text

#### Scenario: Sensitive diagnostics never leak

- GIVEN a failure value contains `com.example.StorageObjectNotFoundException`, stack frames, `bucket/key`, URL, token, internal ID, or `Request failed`
- WHEN any publishing failure, retry, delete, or reschedule error is rendered
- THEN none of those raw values MUST appear in visible UI
- AND only safe localized copy MAY be shown

#### Scenario: Blocked reason is treated as untrusted input

- GIVEN a blocked publication contains a missing, unknown, historical, or raw `blockedReason`
- WHEN the user opens publication details
- THEN the UI MUST map that value through the same safe allowlist/fallback boundary
- AND it MUST NOT render the raw blocked reason

### Requirement: Localized Failure Copy and Actions

All user-facing publishing failure messages, labels, explanations, and recovery actions MUST be internationalized in English and Spanish. Visible strings for failed-publication diagnostics and modal action failures MUST NOT be hardcoded in components, stores, or tests except as locale fixtures/assertions.

#### Scenario: English and Spanish copy parity

- GIVEN the app supports English and Spanish locales
- WHEN known publishing failure categories are rendered
- THEN each locale MUST provide equivalent title, explanation, and action copy
- AND locale key parity tests MUST cover the added keys

#### Scenario: Action failure uses localized safe copy

- GIVEN retry, delete, or reschedule fails from the post detail modal
- WHEN the error is shown to the user
- THEN the UI MUST render a localized safe action-failure message
- AND it MUST NOT render raw `Error.message` or backend `ProblemDetail.detail`

#### Scenario: Structured action failure provides safe recovery guidance

- GIVEN retry, delete, or reschedule fails with a structured API code or HTTP status for unauthorized, not found, state conflict, validation, or temporary unavailability
- WHEN the error is shown to the user
- THEN the UI MUST map HTTP 401/403 to unauthorized, 404 to not found, 409 to state conflict, 400/422 to validation, and 429/network/5xx to temporarily unavailable
- AND an explicitly allowlisted backend error code MAY refine the matching safe status category without introducing backend text
- AND it MUST combine the reason with operation-specific recovery guidance
- AND an unrecognized structured value MUST use the localized unknown fallback

### Requirement: Unknown and Historical Failure Compatibility

Unknown, unmapped, missing, or historical failed and blocked reason codes MUST resolve to safe localized generic or category messages. The system MUST NOT pass raw codes or messages through to visible UI. New backend `FAILED` and `BLOCKED` outcomes MUST persist a category from the canonical taxonomy: `MEDIA_NOT_FOUND`, `MEDIA_UNAVAILABLE`, `PROVIDER_VALIDATION_FAILED`, `PROVIDER_RATE_LIMITED`, `PROVIDER_UNAVAILABLE`, `ACCOUNT_RECONNECT_REQUIRED`, `ACCOUNT_UNAVAILABLE`, or `PUBLISHING_FAILED`.

#### Scenario: Unknown error uses generic fallback

- GIVEN a failed publication has an unknown code such as `UnexpectedProviderClientException`
- WHEN the user opens publication details
- THEN the UI MUST show a localized generic publishing failure message and action
- AND the raw code MUST NOT appear

#### Scenario: Historical exception-class codes remain safe

- GIVEN an old persisted failed publication stores an exception class as `last_error_code`
- WHEN the calendar result is displayed
- THEN the UI MUST map it to a safe localized generic or category message
- AND it MUST NOT require a database migration to avoid leakage

#### Scenario: New failed outcomes use stable categories

- GIVEN async publishing exhausts retries for media, auth, provider outage, validation, or unknown failures
- WHEN terminal failure state is persisted
- THEN the user-facing failure code MUST be a canonical stable product category
- AND exception type/message MUST remain server-side diagnostics only

#### Scenario: New reconnect outcomes use a stable blocked category

- GIVEN publishing cannot continue until the social account is reconnected
- WHEN the publication transitions to `BLOCKED`
- THEN the persisted blocked reason MUST be `ACCOUNT_RECONNECT_REQUIRED`
- AND the raw reconnect exception message MUST NOT be persisted as the blocked reason

### Requirement: Typed Failure Classification and Retry Semantics

Async publishing failures MUST carry a typed canonical category and explicit retryability from the boundary that understands the failure. Classification MUST NOT inspect or parse exception messages, provider response bodies, or exception simple names. Unknown exceptions MUST map to `PUBLISHING_FAILED`.

Retryable failures MUST retain the same category across delivery attempts and terminal persistence after retry exhaustion. The required default mappings are:

- missing asset metadata or binary → `MEDIA_NOT_FOUND`, non-retryable;
- temporary media/storage access failure → `MEDIA_UNAVAILABLE`, retryable;
- provider/capability rejection → `PROVIDER_VALIDATION_FAILED`, non-retryable;
- provider HTTP 429 → `PROVIDER_RATE_LIMITED`, retryable;
- provider network or HTTP 5xx failure → `PROVIDER_UNAVAILABLE`, retryable;
- expired/revoked credentials or insufficient scopes → `ACCOUNT_RECONNECT_REQUIRED`, blocked;
- disabled/deleted terminal account → `ACCOUNT_UNAVAILABLE`, non-retryable;
- unexpected exception → `PUBLISHING_FAILED`, non-retryable.

#### Scenario: Retryable category survives retry exhaustion

- GIVEN provider dispatch returns a typed `PROVIDER_RATE_LIMITED` failure
- WHEN the worker retries and eventually exhausts the configured budget
- THEN every failed delivery attempt MUST retain `PROVIDER_RATE_LIMITED`
- AND terminal publication state MUST persist `PROVIDER_RATE_LIMITED`

#### Scenario: Unknown exception never uses its message as classification

- GIVEN the worker receives an unexpected exception whose type or message contains technical details
- WHEN it classifies the failure
- THEN the publication MUST use `PUBLISHING_FAILED`
- AND no category decision MAY depend on exception name or message text

### Requirement: Guarded Pre-Dispatch and Provider Execution

Media metadata resolution, capability validation, credential resolution, asset download/upload, and provider dispatch MUST execute inside the worker failure boundary. A failure at any of these stages MUST record a failed delivery attempt and follow the typed retry, blocked, or terminal transition contract. No claimed job MAY remain without an explicit reschedule, blocked completion, successful completion, or terminal failure solely because a pre-dispatch dependency threw.

#### Scenario: Media resolution fails before provider dispatch

- GIVEN a claimed publication references media and media resolution is temporarily unavailable
- WHEN resolution fails before provider dispatch
- THEN the worker MUST record a `MEDIA_UNAVAILABLE` failed attempt
- AND it MUST reschedule or fail the job according to the existing retry budget
- AND it MUST NOT call the provider

#### Scenario: Missing media fails safely before provider dispatch

- GIVEN media resolution proves that a required asset or binary no longer exists
- WHEN the worker prepares the publication
- THEN it MUST record a non-retryable `MEDIA_NOT_FOUND` attempt
- AND it MUST mark the publication and job failed atomically
- AND it MUST NOT call the provider

### Requirement: Server-Side Diagnostic Redaction

Publication state and existing notification events MUST contain only canonical categories and safe non-technical copy. New async worker failures MUST leave publication `lastErrorMessage` null or safe and MUST NOT copy `Throwable.message`, provider bodies, or storage paths into publications or notification events.

Delivery attempts and logs MAY retain sanitized server-side diagnostics such as exception type, provider HTTP status, and non-secret provider correlation IDs. Sanitization MUST occur before persistence and MUST remove access/refresh tokens, authorization headers or URLs, provider response bodies, stack traces, identifiers embedded in messages, and raw bucket/object paths.

#### Scenario: Provider response is redacted before persistence

- GIVEN a provider failure contains a response body, URL, token-like value, internal identifier, or stack text
- WHEN the worker records the failure and its notification event
- THEN publication and notification data MUST contain only canonical/safe values
- AND persisted attempt diagnostics MUST exclude every prohibited raw value

#### Scenario: Calendar API exposes no technical message

- GIVEN a publication has a canonical failed or blocked category and server-side attempt diagnostics
- WHEN the calendar/detail API serializes the publication
- THEN the client-visible result MUST expose only the opaque category required for safe mapping
- AND it MUST NOT expose publication or attempt diagnostic messages

### Requirement: Safe Deployment Compatibility

The frontend unknown/missing/historical fallback MUST be deployed before the backend begins persisting canonical categories. A backend rollback MUST occur before any rollback of the frontend guardrail. The frontend guardrail MUST remain deployed while rows containing canonical or historical untrusted values can still be served.

#### Scenario: Backend taxonomy is rolled back

- GIVEN the backend has already persisted one or more canonical categories
- WHEN backend taxonomy writers are rolled back
- THEN the frontend safe fallback MUST remain active
- AND those persisted values MUST NOT become raw visible content

