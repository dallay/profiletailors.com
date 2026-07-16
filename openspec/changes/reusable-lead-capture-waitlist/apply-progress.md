# Apply Progress: Reusable Lead Capture Waitlist Capability

## Change

`reusable-lead-capture-waitlist`

## Delivery Strategy

- Approved strategy: `size-exception`
- Rationale: PR #340 already contains the shared-module foundation implementation. Splitting retroactively would add operational cost and risk. Later persistence, HTTP, rate limiting, marketing, and documentation phases remain tracked separately.

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

### Phase 8 — Comprehensive Tests, completed subset

- [x] 8.1 Domain tests in `shared/lead-capture/waitlist/src/test/`.
- [x] 8.2 Application tests for `JoinWaitlistHandler`.
- [x] 8.5 ArchUnit/module-boundary tests asserting shared modules are framework-free.

## Code Changes in This Apply Continuation

- Restored and kept the CodeRabbit sealed `WaitlistEntryRepository.SaveResult` contract.
- Updated `JoinWaitlistHandler` to branch exhaustively on `SaveResult.Saved` vs `SaveResult.AlreadyExists`.
- Updated repository/handler tests to use the sealed result contract.
- Fixed tests to construct `NormalizedEmail` through `NormalizedEmail.from(EmailAddress(...))` after the constructor was made private.
- Added the missing `com.profiletailors.common..` forbidden-dependency ArchUnit assertion from task 1.1.
- Updated `tasks.md` checkboxes for verified completed work only.

## Commands Run

| Command | Exit | Evidence |
|---|---:|---|
| `./gradlew :shared:lead-capture:waitlist:test --tests 'com.profiletailors.leadcapture.waitlist.application.ports.WaitlistEntryRepositoryTest'` | 1 | RED: failed because `SaveResult` was not yet defined and several tests still used private `NormalizedEmail` constructor. |
| `./gradlew :shared:lead-capture:waitlist:test --tests 'com.profiletailors.leadcapture.waitlist.application.ports.WaitlistEntryRepositoryTest'` | 0 | Focused GREEN after restoring sealed `SaveResult` and adapting tests. |
| `./gradlew :shared:lead-capture:common:test :shared:lead-capture:waitlist:test` | 1 | Broader module verification initially failed because duplicate handler path lacked `idGenerator` mock setup. |
| `./gradlew :shared:lead-capture:common:test :shared:lead-capture:waitlist:test` | 0 | Shared lead-capture modules passed after fixing the handler test. |
| `./gradlew test` | 0 | Full unfiltered configured apply test command passed. |

## Remaining Tasks

- Phase 4 persistence tasks remain incomplete: Liquibase changelog tests, changelogs, R2DBC repository tests/implementations, seed data.
- Phase 5 HTTP endpoint tasks remain incomplete: WebTestClient coverage and controller/DTO implementation.
- Phase 6 rate limiting tasks remain incomplete.
- Phase 7 marketing integration tasks remain incomplete.
- Phase 8 remaining comprehensive tests for persistence, HTTP, frontend, and CI wiring remain incomplete.
- Phase 9 documentation/archive tasks remain incomplete.

## Deviations

- None for the shared foundation/application slice. The `SaveResult` sealed interface is intentionally kept per review/autofix context to preserve explicit atomic save outcomes.

## Status

17 of 47 tasks are complete. Shared foundation/domain/application work for DALLAY-437 is verified and ready for SDD verify or the next implementation slice, depending on whether this SDD change continues beyond Phase 1.
