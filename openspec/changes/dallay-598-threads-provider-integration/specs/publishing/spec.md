# Delta for Publishing

## ADDED Requirements

### Requirement: Threads Catalog and Connection Lifecycle

The catalog MUST expose configured `THREADS` with `PERSONAL_PROFILE` only when implementation and configuration are valid. Provider-aware connection flows MUST reuse the workspace model; LinkedIn routes remain aliases. Completion MUST persist connection and account atomically, and disconnect MUST invalidate credentials before removal. Read models MUST omit credentials and raw provider payloads.

#### Scenario: Configured Threads is available

- GIVEN Threads is enabled with valid configuration
- WHEN a workspace member loads the provider catalog
- THEN `THREADS` and `PERSONAL_PROFILE` MUST be available
- AND no secret or raw provider payload may appear

#### Scenario: Reconnect and disconnect are safe

- GIVEN a workspace has a Threads personal profile
- WHEN it is reconnected or disconnected
- THEN reconnect MUST upsert without duplicates
- AND disconnect MUST invalidate credentials before removing the channel

### Requirement: Threads Credential Lifecycle

The system MUST exchange short-lived Threads credentials for long-lived credentials, store them encrypted with scopes and returned expiry metadata, and refresh ahead of expiry. APIs, logs, audit metadata, delivery evidence, and errors MUST omit tokens, authorization codes, client secrets, signed media URLs, and raw provider payloads.

#### Scenario: Credential refresh precedes delivery

- GIVEN stored credentials are within the refresh-ahead window
- WHEN delivery resolves credentials
- THEN the system MUST refresh and persist returned expiry metadata
- AND use only the refreshed credential

### Requirement: Threads Capabilities and Temporary Media

Threads MUST support text, one image, one video, and mixed-media carousels of 2–20 items. Unsupported combinations, counts, media types, or unready assets MUST fail validation before provider I/O. Provider-fetchable media MUST use temporary HTTPS URLs valid through the bounded workflow; storage MUST NOT become permanently public and media-library readiness remains authoritative.

#### Scenario: Invalid content is rejected early

- GIVEN a Threads draft has unsupported media or an invalid carousel count
- WHEN it is validated for queueing or delivery
- THEN the system MUST return a provider validation error
- AND MUST NOT create a provider container

#### Scenario: Ready media is temporary and fetchable

- GIVEN all referenced media-library assets are `READY`
- WHEN container creation begins
- THEN each URL MUST be HTTPS and valid through the workflow margin
- AND permanent public storage MUST NOT be required

### Requirement: Threads Containers, Scheduling, and Evidence

The adapter MUST create required child containers, poll with configured bounds, finalize once, and persist container IDs, final remote IDs, statuses, phases, and stable `providerOperationRef`. That reference MUST support idempotency and reconciliation but MUST NOT prove final publication. Ambiguous outcomes MUST use the shared taxonomy and safe correlation metadata. Threads MUST reuse existing publication, scheduling, queue, worker, retry, calendar, composer, and delivery-attempt workflows.

#### Scenario: Bounded carousel succeeds

- GIVEN a valid 2–20 item Threads carousel
- WHEN creation, bounded polling, and finalization succeed
- THEN the publication MUST be `PUBLISHED`
- AND remote IDs and `providerOperationRef` MUST be persisted

#### Scenario: Finalization is ambiguous

- GIVEN finalization may have succeeded remotely but the response times out
- WHEN the worker records the attempt
- THEN it MUST record `AMBIGUOUS` with safe references
- AND retry only after lookup proves no final object exists

#### Scenario: Scheduled delivery reuses the worker

- GIVEN a valid Threads publication uses `SCHEDULED_AT` or `NEXT_SLOT`
- WHEN it becomes due
- THEN the existing worker MUST claim it
- AND no provider-specific scheduler or second composer may be created

### Requirement: Product Truth and Rollout Evidence

Product truth MUST describe Threads MVP behavior without claiming Meta App Review, deployed smoke execution, or production readiness absent current evidence. Rollout records MUST distinguish local, CI, deployed test/app-role smoke, and Meta App Review evidence. The dashboard MUST follow catalog state and expose no unsupported Threads analytics, replies, ads, pages, or second composer.

#### Scenario: Missing external evidence blocks readiness claims

- GIVEN implementation exists without current smoke or App Review evidence
- WHEN rollout status is reported
- THEN it MUST NOT claim production readiness
- AND catalog enablement MUST remain policy and configuration controlled
