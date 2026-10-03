# Tasks: Enhance Backoffice Waitlist Management

## Review Workload Forecast

| Field                   | Value                                                                                             |
|-------------------------|---------------------------------------------------------------------------------------------------|
| Estimated changed lines | 550–750                                                                                           |
| 400-line budget risk    | High                                                                                              |
| Chained PRs recommended | Yes                                                                                               |
| Suggested split         | PR 1: G1+G2 (backend cancel/resend locks + frontend basic), PR 2: G3+G4+G6+G7+G8 (UI enrichments) |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High

### Suggested Work Units

| Unit | Goal                                                                                              | Likely PR | Notes                                                                                                                                                       |
|------|---------------------------------------------------------------------------------------------------|-----------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1    | G1 optimistic lock + G2 resend backend                                                            | PR 1      | Backend: add `expectedVersion` to command, handler 409 on mismatch, wire new resend endpoint; Frontend: CancelDialog sends version, ResendEntryDialog wired |
| 2    | G3 consent fields + G4 summary card + G6 waitlistKey filter + G7 date-range filters + G8 metadata | PR 2      | Frontend UI enrichments; Vitest specs in PR 2 after UI stabilizes                                                                                           |

## Phase 1: Backend — G1 Optimistic Lock on Cancel (PR 1 Foundation)

- [ ] 1.1 Add `expectedVersion: Long` field to `CancelWaitlistEntryCommand` in
  `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/command/AdminCommands.kt`
- [ ] 1.2 Add `WaitlistEntryVersionMismatchException` in
  `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/`
- [ ] 1.3 Update `CancelWaitlistEntryHandler.handle()` to check version against fetched entry and
  throw `WaitlistEntryVersionMismatchException` on mismatch (409 semantics)
- [ ] 1.4 Add 409 response to `AdminWaitlistController.cancel()` endpoint for
  `WaitlistEntryVersionMismatchException`
- [ ] 1.5 Add unit test `CancelWaitlistEntryHandlerTest` scenario:
  `handle given stale expectedVersion throws WaitlistEntryVersionMismatchException`
- [ ] 1.6 Add integration test `AdminWaitlistControllerTest` scenario:
  `cancel with stale expectedVersion returns 409`

## Phase 2: Backend — G2 Resend Endpoint Wiring (PR 1)

- [ ] 2.1 Add `POST /api/admin/invitations/{invitationId}/resend` to `AdminInvitationController`
  delegating to `ResendWaitlistInvitationHandler`
- [ ] 2.2 Add `AdminInvitationControllerTest` scenario: `resend returns 200 and invokes handler`,
  `resend for non-invited entry returns 404`
- [ ] 2.3 Verify `InvitationNotResendableException` maps to 409 in controller advice (already wired
  per existing handler tests)

## Phase 3: Frontend — G1 Cancel Dialog + Optimistic Lock (PR 1)

- [ ] 3.1 Add `CancelWaitlistEntryDialog.vue` component (similar to `RevokeInvitationDialog`) with
  `expectedVersion` prop
- [ ] 3.2 Write RED test: `CancelWaitlistEntryDialog.spec.ts` —
  `emits confirm with expectedVersion in payload`
- [ ] 3.3 GREEN test: implement component — dialog emits
  `{ action: 'confirm', reason, expectedVersion }`
- [ ] 3.4 Update `WaitlistEntryView.vue` to pass `entry.version` to `CancelWaitlistEntryDialog`
- [ ] 3.5 Update `WaitlistView.vue` cancel handler to POST `CancelWaitlistEntryCommand` with
  `expectedVersion` from row data

## Phase 4: Frontend — G2 Resend Action (PR 1)

- [ ] 4.1 Add `ResendEntryDialog.vue` component (opens dialog, POSTs
  `POST /api/admin/invitations/{id}/resend`)
- [ ] 4.2 Write RED test: `ResendEntryDialog.spec.ts` —
  `renders resend button, emits confirm, calls resend API`
- [ ] 4.3 GREEN test: implement `ResendEntryDialog.vue` with `invitationId` prop and success/error
  handling
- [ ] 4.4 Discover active invitation ID in `WaitlistEntryView.vue` from `invitationHistory[0].id`
  (first ACTIVE entry)
- [ ] 4.5 Wire resend button in `WaitlistEntryView.vue` with `ResendEntryDialog`

## Phase 5: Backend — G6/G7/G8 Query Enrichments (PR 2)

- [ ] 5.1 Update `AdminWaitlistQuery` interface to include `waitlistKey`, `joinedFrom`, `joinedTo`,
  `invitedFrom`, `invitedTo` parameters
- [ ] 5.2 Update `R2dbcAdminWaitlistQuery` to apply all 5 filters in the SQL query
- [ ] 5.3 Update `AdminWaitlistController.list()` to accept and pass `waitlistKey`, `joinedFrom`,
  `joinedTo`, `invitedFrom`, `invitedTo` query params
- [ ] 5.4 Add integration test `R2dbcAdminWaitlistQueryPostgresIntegrationTest` for `waitlistKey`
  filter and date ranges
- [ ] 5.5 Update `AdminWaitlistEntryDetail` to include `metadata: WaitlistMetadataSummary?` (backend
  model already surfaces it; ensure controller maps it)

## Phase 6: Frontend — G3 Consent Fields (PR 2)

- [ ] 6.1 Write RED test: `WaitlistEntryView.spec.ts` —
  `renders earlyAccessConsent, marketingConsent, consentVersion fields`
- [ ] 6.2 GREEN test: add consent display section in `WaitlistEntryView.vue` showing
  `entry.earlyAccessConsent`, `entry.marketingConsent`, `entry.consentVersion`

## Phase 7: Frontend — G4 Status Count Summary Card (PR 2)

- [ ] 7.1 Write RED test: `WaitlistView.spec.ts` — `calls countByStatus, renders status count pills`
- [ ] 7.2 GREEN test: add `countByStatus` call to `WaitlistView.vue` on mount and render pill badges
  for each status
- [ ] 7.3 Mock `GET /api/admin/waitlist-entries/count-by-status` in `WaitlistView.spec.ts`

## Phase 8: Frontend — G6 waitlistKey Filter (PR 2)

- [ ] 8.1 Write RED test: `WaitlistView.spec.ts` —
  `applies waitlistKey filter, adds param to API call`
- [ ] 8.2 GREEN test: add `waitlistKey` ref and filter input in `WaitlistView.vue` search bar
- [ ] 8.3 Pass `waitlistKey` as query param in `fetchEntries()`

## Phase 9: Frontend — G7 Date-Range Filters (PR 2)

- [ ] 9.1 Write RED test: `WaitlistView.spec.ts` —
  `applies joinedFrom/joinedTo/invitedFrom/invitedTo, adds params to API call`
- [ ] 9.2 GREEN test: add date inputs for `joinedFrom`, `joinedTo`, `invitedFrom`, `invitedTo` in
  filter section of `WaitlistView.vue`
- [ ] 9.3 Pass all date params in `fetchEntries()` URLSearchParams

## Phase 10: Frontend — G8 Metadata Summary (PR 2)

- [ ] 10.1 Write RED test: `WaitlistEntryView.spec.ts` —
  `renders metadata section when entry.metadata is present`
- [ ] 10.2 GREEN test: add metadata display section in `WaitlistEntryView.vue` (source, UTM fields,
  referrer)

## Phase 11: G5 Comprehensive Vitest Specs (PR 2)

- [x] 11.1 Write RED: `WaitlistView.spec.ts` — test list loading, pagination, status filter, cancel
  dialog open/close, error state
- [x] 11.2 GREEN: implement full `WaitlistView.spec.ts` covering all scenarios
- [x] 11.3 Write RED: `WaitlistEntryView.spec.ts` — test entry detail loading, consent display,
  metadata display, resend button visibility, cancel button
- [x] 11.4 GREEN: implement full `WaitlistEntryView.spec.ts` covering all scenarios

## Phase 12: Quality Gates

- [ ] 12.1 `just admin-check` — no new type errors introduced
- [ ] 12.2 `just admin-test` — all new and existing Vitest tests pass
- [ ] 12.3 `just backend-check` — Detekt clean, all unit tests pass
- [ ] 12.4 `just backend-bdd-fast` — platform-admin BDD scenarios pass (if new scenarios added)
- [ ] 12.5 Verify `AdminWaitlistControllerTest` and `CancelWaitlistEntryHandlerTest` pass after G1
  changes
- [ ] 12.6 Verify `AdminInvitationControllerTest` resend scenarios pass after G2 changes

## Implementation Order

G1 (cancel lock) is the most critical — it prevents data corruption. Start there. G2 (resend) reuses
an existing handler but needs endpoint wiring. Both are PR 1.

G3–G8 are UI enrichments that can be tested in isolation; batch them in PR 2 after the foundational
G1/G2 work stabilizes.

Backend test additions follow the same PR split: G1/G2 handler/controller tests in PR 1; query
integration tests in PR 2.
