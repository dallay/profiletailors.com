# Tasks: Bulk Waitlist Invitation Operations (#665)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 550–750 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR1 → PR2 → PR3 |
| Delivery strategy | ask-on-risk |
| Chain strategy | stacked-to-main |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Bulk command + handler + unit/app tests | PR1 → main | No route; fakes only |
| 2 | Bulk route + telemetry + API/integration tests | PR2 → main | Depends on PR1; thin controller, no @Transactional |
| 3 | BDD + admin UI + docs | PR3 → main | Depends on PR2; feature-flagged UI |

## Phase 1: Contracts (TDD foundation)

- [x] 1.1 RED: add failing unit test for `BulkInviteWaitlistEntriesCommand` cap 50, empty/blank, dedupe in `BulkInviteHandlerTest.kt`
- [x] 1.2 GREEN: add `BulkInviteWaitlistEntriesCommand`, `BulkEntryResult`, `BulkInviteSummary` to `AdminCommands.kt`
- [x] 1.3 RED: add failing test for exception→outcome map (INVITED→skipped, converted→failed, contention→VERSION_CONFLICT)

## Phase 2: Handler (TDD core)

- [x] 2.1 GREEN: create `BulkInviteWaitlistEntriesHandler.kt` — cap check, up-front `WAITLIST_INVITE` fail-fast, sequential `runAtomically` per entry
- [x] 2.2 GREEN: implement asymmetric audit (success inside txn, skipped/failed via `catch` outside) + `INVITED` short-circuit, no `InvitationIssued` on skip
- [x] 2.3 Verify: per-entry independence + retry→skipped tests pass via `just backend-test-fast`

## Phase 3: Wiring (TDD integration)

- [x] 3.1 RED: failing WebFlux slice test for `POST /invitations:bulk` 200 envelope+summary, 400 over-cap, 403 before any entry touched
- [x] 3.2 GREEN: add bulk route to `AdminWaitlistController.kt` (thin mapping only) + wire bean in `PlatformAdminBootstrapConfiguration.kt`
- [x] 3.3 RED/GREEN: add `recordBulkInvite` to `InvitationTelemetry.kt` + `InvitationObservability.kt`, assert bulk + per-entry counters
- [x] 3.4 GREEN: R2DBC integration test — mixed batch commits independently, contention yields one invited + one `VERSION_CONFLICT`

## Phase 4: Acceptance + surface

- [x] 4.1 Add BDD to `platform-admin.feature`: mixed 3+1+1 batch, retry→skips, bulk-vs-single contention, 403 denial; run `just backend-bdd-fast`
- [x] 4.2 Add bulk selection + per-entry results to `WaitlistView.vue` behind `platform.waitlist.invite` (IDs+codes only) with Vitest coverage
- [x] 4.3 Update delta specs + rollout note (dual-write parity debt, cap 50); run Detekt/lint gates
