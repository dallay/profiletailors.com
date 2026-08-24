# Apply Progress: `dallay-571-back-office-waitlist-queries`

## Overview

- **Change:** `dallay-571-back-office-waitlist-queries`
- **Scope:** Back Office waitlist queries, filtering, and search (list with pagination, search
  by email, filter by status, detail exposing invitation history, authorization, and
  aggregate-level observability).
- **Delivery:** Single PR (#838) merged 2026-08-24, plus a post-review boundary fix (`02b698d2`)
  and a review-cleanup commit (`47eb8986`).
- **Implementation PR:** https://github.com/dallay/profiletailors.com/pull/838

## Changes

### Code

- `WaitlistQueryTelemetryPort` — framework-free port in
  `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/ports/`.
- `WaitlistQueryObservabilityAdapter` — Micrometer implementation in
  `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/observability/`.
- `AdminWaitlistController.listEntries` — added `WaitlistQueryTelemetryPort` constructor
  parameter and `recordListQuery` call after a successful `waitlistQuery.list` invocation; the
  call records `statusFilterApplied = !status.isNullOrBlank()` and
  `emailSearch = !email.isNullOrBlank()` to keep the counter tags low-cardinality and
  boolean-only.
- `WaitlistQueryObservabilityAdapter` — registers `platform.admin.waitlist.queries` counter
  with description "Platform admin waitlist list queries" and boolean tags `status.filter`
  and `email.search`.

### Tests

- `platform-admin.feature` — added two `@fast @postgres @platform-admin` scenarios:
  - `Platform operator can search waitlist entries by email`
  - `Platform operator can filter waitlist entries by status`
  Both scenarios assert the response status, entry count, and entry email.
- `PlatformAdminBddSteps.kt` — added step definitions for `searches the waitlist for`,
  `filters the waitlist by status`, `should contain {int} entries`, and `should contain an
  entry with email`.
- `AdminWaitlistControllerTest.kt` — adjusted to inject the new telemetry dependency and
  assert the telemetry interaction on list invocations.
- `WaitlistQueryObservabilityAdapterTest.kt` — new unit test for the Micrometer adapter
  recording the expected counter and tag values.

### Spec

- `openspec/changes/dallay-571-back-office-waitlist-queries/{proposal,spec,state,tasks}.md`
  — added by PR #838 alongside the code.

## Status

- [x] T1 — `AdminWaitlistController` exposes pagination, email search, status filter, and
  bounded sort fields.
- [x] T2 — `WaitlistQueryTelemetryPort` and `WaitlistQueryObservabilityAdapter` record
  aggregate query counts.
- [x] T3 — Telemetry wired into `AdminWaitlistController.listEntries`.
- [x] T4 — `AdminWaitlistControllerTest` updated for the new dependency and telemetry
  assertions.
- [x] T5 — BDD scenarios added to `platform-admin.feature`.
- [x] T6 — BDD step definitions wired in `PlatformAdminBddSteps`.
- [x] T7 — `R2dbcAdminWaitlistQueryPostgresIntegrationTest` covers pagination, status filter,
  email search, and detail with invitation history.
- [x] T8 — `just backend-bdd-fast` passes.
- [x] T9 — Waitlist unit tests pass.
- [x] T10 — OpenSpec change artifacts (proposal, spec, state, tasks) added.

## Runtime Evidence Captured

Recorded by the independent verification pass on 2026-08-24:

- `just backend-test exclude-tags="postgres"` — BUILD SUCCESSFUL in 12m 24s.
- `just backend-bdd-fast` — BUILD SUCCESSFUL in 4m 58s.
- `server/smp/build/test-results/bddFastTest/TEST-feature_classpath_features-platform-admin.feature.xml` — 19 tests, 0 failures, 0 errors, 0 skipped.

## Open Follow-ups

None for this change. Implementation is feature-complete against the delta, PR is merged,
verification is PASS WITH WARNINGS with no CRITICAL/P0/P1 findings, and the post-merge
follow-up commit `02b698d2` (`fix(smp): enforce hexagonal layer boundaries in media and
platformadmin`) confirms the controller and ports remain in their declared hexagonal layers.
