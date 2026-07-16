# Apply Progress: Reusable Lead Capture Waitlist Capability

## Change

`reusable-lead-capture-waitlist`

## Delivery Strategy

- Approved strategy: `size-exception`
- Current slice: DALLAY-438 persistence only.
- Rationale: Broader SDD change exceeds the standard review budget, but this apply scope is constrained to backend persistence and seed data. Endpoint, rate limiting, marketing integration, and documentation remain out of scope.

## Completed Tasks

### Phase 1 — Foundation / Shared Module Boundaries (DALLAY-437)

- [x] 1.1 ArchUnit/module-boundary tests exist for forbidden dependencies under `shared/lead-capture/**`.
- [x] 1.2 Gradle auto-discovers `:shared:lead-capture:common` and `:shared:lead-capture:waitlist` via `settings.gradle.kts` recursive `shared` scanning.
- [x] 1.3 Manifest/import-level framework isolation is covered by ArchUnit checks for Spring/R2DBC/server/common dependencies.
- [x] 1.4 Shared module `build.gradle.kts` files remain framework-free and only declare Kotlin/test dependencies plus `waitlist -> common`.

### Phase 2 — Domain (shared) (DALLAY-437)

- [x] 2.1 Common value-object tests exist for `EmailAddress`, `NormalizedEmail`, `CaptureSource`, `CaptureLocale`, `LeadMetadata`.
- [x] 2.2 Common value objects are implemented under `shared/lead-capture/common`.
- [x] 2.3 Waitlist aggregate/status tests exist.
- [x] 2.4 `Waitlist` and `WaitlistStatus` are implemented.
- [x] 2.5 Waitlist entry/status/consent tests exist.
- [x] 2.6 `WaitlistEntry`, status transitions, lifecycle invariants, and `WaitlistConsent` are implemented.

### Phase 3 — Application (shared) (DALLAY-437)

- [x] 3.1 Port contract tests exist for `WaitlistRepository` and `WaitlistEntryRepository`.
- [x] 3.2 Ports are defined in `shared/lead-capture/waitlist/application/ports`.
- [x] 3.3 `JoinWaitlistCommand` / `JoinWaitlistHandler` tests cover accepted/idempotent join behavior and missing/invalid scenarios covered by domain consent construction.
- [x] 3.4 `JoinWaitlistHandler` is implemented with atomic `saveIfNotExists` semantics.

### Phase 4 — Persistence (DALLAY-438)

- [x] 4.1 Liquibase changelog tests assert the master includes lead-capture changelogs and the schema changelog defines `waitlists`, `waitlist_entries`, `UNIQUE(waitlist_id, normalized_email)`, and indexes including `status`, `source`, and `form_id`.
- [x] 4.2 Liquibase schema changelog creates `waitlists` and `waitlist_entries` with the required DALLAY-438 indexes.
- [x] 4.3 Postgres-backed repository tests cover seeded waitlist lookup, entry round-trip, same-waitlist dedupe, and cross-waitlist reuse.
- [x] 4.4 R2DBC adapters implement `WaitlistRepository` and the sealed `WaitlistEntryRepository.SaveResult` contract.
- [x] 4.5 Repository test asserts `profile-tailors-launch` exists after migrations.
- [x] 4.6 Liquibase seed changelog inserts active `profile-tailors-launch` waitlist.

### Phase 8 — Comprehensive Tests, completed subset

- [x] 8.1 Domain tests in `shared/lead-capture/waitlist/src/test/`.
- [x] 8.2 Application tests for `JoinWaitlistHandler`.
- [x] 8.3 R2DBC repository tests are Postgres-tagged and run against Testcontainers.
- [x] 8.5 ArchUnit/module-boundary tests asserting shared modules are framework-free.

## Code Changes in This Apply Continuation

- Fixed the DALLAY-438 verify gap by strengthening `LeadCaptureLiquibaseChangelogTest` to assert `idx_waitlist_entries_status`, `idx_waitlist_entries_source`, and `idx_waitlist_entries_form_id`, then adding those indexes to `001-create-waitlists.yaml`.
- Added lead-capture Liquibase changelogs to create `waitlists` and `waitlist_entries`, including per-waitlist dedupe on `(waitlist_id, normalized_email)` and supporting indexes.
- Added a seed changelog for active `profile-tailors-launch` waitlist.
- Included lead-capture changelogs from the master Liquibase changelog.
- Added `R2dbcWaitlistRepository` and `R2dbcWaitlistEntryRepository` infrastructure adapters under `server/smp`.
- Preserved the sealed `WaitlistEntryRepository.SaveResult` contract by returning `Saved` or `AlreadyExists` from `saveIfNotExists`.
- Added Postgres-backed persistence tests plus a changelog presence/shape test.
- Added server dependency edges to `:shared:lead-capture:common` and `:shared:lead-capture:waitlist`.
- Renamed lead-capture shared module archive names and group IDs to avoid colliding with existing `:shared:common` artifact coordinates on the server classpath.
- Added lead-capture cleanup statements to Postgres test support and updated cleanup ordering coverage.
- Added `leadcapture` Modulith metadata for the new server bounded context.

## Commands Run

| Command | Exit | Evidence |
|---|---:|---|
| `./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.infrastructure.persistence.LeadCaptureLiquibaseChangelogTest'` | 1 | RED: failed first on missing changelog files/master includes; after fixing test path typo, failed on absent lead-capture changelogs. |
| `./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.infrastructure.persistence.LeadCaptureLiquibaseChangelogTest'` | 0 | GREEN for Liquibase changelog and seed shape after adding changelogs and master includes. |
| `./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.infrastructure.persistence.R2dbcWaitlistRepositoriesPostgresTest'` | 1 | RED: failed compile because R2DBC repository classes did not exist and server lacked shared lead-capture dependencies. |
| `SMP_POSTGRES_TEST_PASSWORD=profiletailors_test ./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.infrastructure.persistence.R2dbcWaitlistRepositoriesPostgresTest'` | 1 | RED/GREEN iteration: after implementing adapters, exposed classpath collision with `:shared:common`, then cleanup/dedup setup issues. |
| `SMP_POSTGRES_TEST_PASSWORD=profiletailors_test ./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.infrastructure.persistence.R2dbcWaitlistRepositoriesPostgresTest'` | 0 | Focused GREEN for Postgres repository tests after unique shared module coordinates and cleanup fixes. |
| `SMP_POSTGRES_TEST_PASSWORD=profiletailors_test ./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.infrastructure.persistence.R2dbcWaitlistRepositoriesPostgresTest' --tests 'com.profiletailors.smp.leadcapture.infrastructure.persistence.LeadCaptureLiquibaseChangelogTest' --tests 'com.profiletailors.smp.integration.support.PostgresTestContainerSupportTest'` | 0 | Focused regression pass for DALLAY-438 tests and updated cleanup support test. |
| `SMP_POSTGRES_TEST_PASSWORD=profiletailors_test ./gradlew :server:smp:test` | 0 | Broader unfiltered server module test passed. |
| `./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.infrastructure.persistence.LeadCaptureLiquibaseChangelogTest'` | 1 | RED for DALLAY-438 verify gap: failed after adding assertions for `idx_waitlist_entries_status`, `idx_waitlist_entries_source`, and `idx_waitlist_entries_form_id` while the changelog still lacked those indexes. |
| `./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.infrastructure.persistence.LeadCaptureLiquibaseChangelogTest'` | 0 | GREEN after adding the missing `status`, `source`, and `form_id` indexes to `001-create-waitlists.yaml`. |
| `SMP_POSTGRES_TEST_PASSWORD=profiletailors-test ./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.infrastructure.persistence.LeadCaptureLiquibaseChangelogTest' --tests 'com.profiletailors.smp.leadcapture.infrastructure.persistence.R2dbcWaitlistRepositoriesPostgresTest'` | 0 | Focused DALLAY-438 verification passed with Testcontainers password set. |
| `SMP_POSTGRES_TEST_PASSWORD=profiletailors-test ./gradlew :server:smp:test` | 0 | Broader unfiltered server module test passed after the changelog fix. |

## Remaining Tasks

- Phase 5 HTTP endpoint tasks remain incomplete: WebTestClient coverage and controller/DTO implementation.
- Phase 6 rate limiting tasks remain incomplete.
- Phase 7 marketing integration tasks remain incomplete.
- Phase 8 remaining comprehensive tests for HTTP, frontend, and CI wiring remain incomplete.
- Phase 9 documentation/archive tasks remain incomplete.

## Deviations

- No functional deviation from the DALLAY-438 persistence design after this continuation. The persisted column uses `normalized_email`, matching existing domain naming and tests, while the OpenSpec task text says `email_normalized`; the requirement/design explicitly require `UNIQUE(waitlist_id, normalized_email)` / normalized email dedupe semantics.
- The shared lead-capture module Gradle `group` and archive names were adjusted because both `:shared:common` and `:shared:lead-capture:common` otherwise produced identical `com.profiletailors:common` coordinates, causing the server classpath to resolve the wrong artifact.

## Status

24 of 47 tasks are complete. DALLAY-438 persistence is implemented and verified; ready for SDD verify or the next backend slice (DALLAY-439 HTTP endpoint) if continuing the broader change.
