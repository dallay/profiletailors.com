# Tasks: Reusable Lead Capture Waitlist Capability

## Overview

This change covers all 7 Linear issues (DALLAY-437 through DALLAY-443). Each issue maps to a phase below. The completed implementation scope is DALLAY-437 and DALLAY-438; the next implementation focus is Phase 5 (DALLAY-439).

## Changes

### Review Workload Forecast

- **Estimated review size**: exceeds the normal 400 changed-line budget because the change spans shared Gradle modules, framework-free Kotlin domain/application code, persistence, HTTP, rate limiting, marketing integration, tests, and documentation.
- **Delivery strategy**: `size-exception`.
- **Rationale**: PR #340 already exists and already contains the foundation implementation. Splitting retroactively would add operational cost and risk. The exception is acceptable because the active implementation focus remains Phase 1 (DALLAY-437), with later phases tracked separately in the checklist.
- **Review expectation**: reviewers should prioritize module boundaries, domain invariants, TOCTOU-safe dedupe semantics, and public API enumeration safety.

### Phase 1 — Foundation / Shared Module Boundaries (DALLAY-437)

- [x] **1.1 RED**: ArchUnit test asserting no class under `shared/lead-capture/**` depends on `com.profiletailors.smp..`, `org.springframework..`, `io.r2dbc..`, `com.profiletailors.common..` (except explicitly allowed common types).
- [x] **1.2 GREEN**: Register Gradle subprojects `:shared:lead-capture:common` and `:shared:lead-capture:waitlist` (auto-discovery via `settings.gradle.kts`).
- [x] **1.3 RED**: Failing manifest test asserting the shared modules contain no Spring/R2DBC annotations or imports.
- [x] **1.4 GREEN**: Configure `build.gradle.kts` with explicit dependency constraints excluding frameworks in shared modules.

### Phase 2 — Domain (shared) (DALLAY-437)

- [x] **2.1 RED**: Failing domain tests for `EmailAddress`, `NormalizedEmail`, `CaptureSource`, `CaptureLocale`, `LeadMetadata`.
- [x] **2.2 GREEN**: Implement the value objects in `shared/lead-capture/common`.
- [x] **2.3 RED**: Failing domain tests for `Waitlist` aggregate and `WaitlistStatus` (`draft`, `active`, `paused`, `closed`, `archived`).
- [x] **2.4 GREEN**: Implement `Waitlist` and status transitions.
- [x] **2.5 RED**: Failing domain tests for `WaitlistEntry`, `WaitlistEntryStatus` (`pending`, `invited`, `converted`, `cancelled`), lifecycle timestamps, and `WaitlistConsent` (with explicit `earlyAccess` / `marketing` / `version`).
- [x] **2.6 GREEN**: Implement `WaitlistEntry`, status transitions, and consent value object.

### Phase 3 — Application (shared) (DALLAY-437)

- [x] **3.1 RED**: Port contract tests for `WaitlistRepository` and `WaitlistEntryRepository`.
- [x] **3.2 GREEN**: Define the ports in `shared/lead-capture/waitlist/application/ports`.
- [x] **3.3 RED**: `JoinWaitlistCommand` / `JoinWaitlistHandler` tests asserting idempotent `accepted` result, dedupe behavior, and rejection on missing `earlyAccess` consent.
- [x] **3.4 GREEN**: Implement the handler.

### Phase 4 — Persistence (DALLAY-438)

- [x] **4.1 RED**: Liquibase change-set test asserting presence of `waitlists` and `waitlist_entries`, plus `UNIQUE (waitlist_id, email_normalized)` and indexes.
- [x] **4.2 GREEN**: Add Liquibase changelogs.
- [x] **4.3 RED**: Repository tests for `R2dbcWaitlistRepository` and `R2dbcWaitlistEntryRepository` (round-trip, dedupe).
- [x] **4.4 GREEN**: Implement R2DBC repositories implementing the shared ports.
- [x] **4.5 RED**: Failing test asserting `profile-tailors-launch` waitlist exists after migrations.
- [x] **4.6 GREEN**: Add Liquibase seed changelog.

### Phase 5 — HTTP Endpoint (DALLAY-439)

- [x] **5.1 RED**: WebTestClient tests for the controller covering 202 (new), 202 (duplicate), 400, 404, 409, 500.
- [x] **5.2 GREEN**: Implement `WaitlistController`, request/response DTOs, validation, mapping to `JoinWaitlistCommand`, and uniform public response.
- [x] **5.3 RED**: Handler-level tests asserting internal `joined_new` vs `already_joined` distinction exists but is not exposed publicly.
- [x] **5.4 GREEN**: Ensure controller swallows the distinction in the public DTO.

### Phase 6 — Rate Limiting (DALLAY-440)

- [x] **6.1 RED**: Integration test asserting 11th request from same IP within a minute returns 429.
- [x] **6.2 GREEN**: Configure `WaitlistController` as a WAITLIST endpoint in `BucketConfigurationFactory`.
- [x] **6.3 RED**: Test asserting rate limit applies even on duplicate joins and validation errors.
- [x] **6.4 GREEN**: Confirm filter ordering covers the waitlist route.
- [x] **6.5 RED/GREEN**: Assert the same IP can exhaust waitlist A while a request to waitlist B remains accepted, then isolate WAITLIST bucket cache identities per route without changing AUTH, BUSINESS, or RESUME behavior.

### Phase 7 — Marketing Integration (DALLAY-441)

- [x] **7.1 RED**: Failing payload-contract Vitest test asserting the form sends the documented payload shape.
- [x] **7.2 GREEN**: Refactor `WaitlistForm.astro` to call `fetch()` to the backend endpoint.
- [x] **7.3 RED**: E2E test asserting the form submits successfully when backend responds 202.
- [x] **7.4 GREEN**: Wire E2E test against stubbed endpoint (Playwright route interception).
- [x] **7.5 RED**: E2E test for invalid-email path (client-side validation blocks submission).
- [x] **7.6 GREEN**: Ensure `noValidate` + JS validation blocks empty/invalid emails.

### Phase 8 — Comprehensive Tests (DALLAY-442)

- [x] **8.1**: Domain tests in `shared/lead-capture/waitlist/src/test/`.
- [x] **8.2**: Application tests for `JoinWaitlistHandler`.
- [x] **8.3**: R2DBC repository tests (Postgres-tagged if needed).
- [x] **8.4**: WebTestClient tests for `WaitlistController`.
- [x] **8.5**: ArchUnit / module-boundary tests asserting the shared modules are framework-free.
- [x] **8.6**: Frontend Vitest + Playwright E2E.
- [x] **8.7**: Wire all of the above into `just ci-local` (Vitest + lint via `just ci-local`; full Playwright E2E runs via `just ci` to keep `ci-local` fast).

### Phase 9 — Documentation (DALLAY-443)

- [x] **9.1**: Flip ADR-0011 status from Proposed to Accepted.
- [x] **9.2**: Update `docs/architecture/shared/dependencies.md` to include new modules.
- [x] **9.3**: Update C4 container and component diagrams.
- [ ] **9.4**: After `sdd-archive`, add canonical specs at `openspec/specs/lead-capture-common/spec.md` and `openspec/specs/lead-capture-waitlist/spec.md`.
- [x] **9.5**: Add ADR-0011 entry to architecture README index.
- [x] **9.6**: Reference ADR-0011 from `docs/architecture/adr/README.md`.

## Usage

Complete phases in order and keep each Linear issue in a focused branch and pull request.

## Troubleshooting

If a phase cannot satisfy its focused verification, leave its tasks unchecked and record the blocker in `apply-progress.md` and `verify-report.md`.

## References

- `proposal.md`
- `spec.md`
- `design.md`
- `apply-progress.md`
- `verify-report.md`
