# Tasks: Back Office Waitlist Queries, Filtering, and Search (DALLAY-571)

## Overview

Deliver operational read access over waitlist entries: list with pagination, search by email,
filter by status, sort by creation date, and detail access exposing invitation/conversion state.
The implementation already exists on the `back-office` branch; this change adds BDD scenario
coverage, query observability, and the OpenSpec contract.

## Changes

### Code

- [x] T1: Verify `AdminWaitlistController` list/detail endpoints expose pagination, email search,
  status filter, date-range filters, and bounded sort fields (`joinedAt`, `invitedAt`, `email`,
  `status`).
- [x] T2: Add `WaitlistQueryTelemetryPort` and `WaitlistQueryObservabilityAdapter` recording
  aggregate query counts tagged by status-filter presence and email-search usage.
- [x] T3: Wire telemetry into `AdminWaitlistController.listEntries`.
- [x] T4: Update `AdminWaitlistControllerTest` for the new dependency and verify telemetry calls.

### Tests

- [x] T5: Add BDD scenarios to `platform-admin.feature` for search by email and filter by status.
- [x] T6: Add BDD step definitions for `searches the waitlist for`, `filters the waitlist by
  status`, `should contain {int} entries`, and `should contain an entry with email`.
- [x] T7: Verify existing integration test `R2dbcAdminWaitlistQueryPostgresIntegrationTest`
  covers pagination, status filter, email search, and detail with invitation history.
- [x] T8: Run `just backend-bdd-fast` and confirm all platform-admin scenarios pass.
- [x] T9: Run the waitlist unit tests and confirm they pass.

### Contract

- [x] T10: Add OpenSpec change `dallay-571-back-office-waitlist-queries` with proposal, spec,
  state, and tasks.

## Usage

### Execution Order

Run unit tests, then BDD-fast. PostgreSQL integration tests are tagged `@postgres` and require
`just infra-up`.

## Troubleshooting

### Blockers

A failing BDD scenario for list, search, or filter; a missing telemetry metric; or a stale
OpenSpec contract blocks acceptance.

## References

- DALLAY-571, DALLAY-560, DALLAY-569.
- `just backend-bdd-fast`, `just backend-test`.
