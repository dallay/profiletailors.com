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
