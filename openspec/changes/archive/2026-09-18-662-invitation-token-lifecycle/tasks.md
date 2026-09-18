# Tasks: Secure Invitation Token Lifecycle

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 450–650 (prod ~150, tests/BDD ~300–450, docs small) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 metrics+CAS → PR 2 bearer+throttle → PR 3 BDD+docs+gates |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Metrics + CAS fixes + unit tests | PR 1 | Base main; no contract change |
| 2 | Bearer scoping/redaction + accept throttle + tests | PR 2 | Base PR 1; needs DALLAY-565 sign-off |
| 3 | Concurrent-winner BDD + docs/ADR/spec + gates | PR 3 | Base PR 2; postgres + bdd-fast evidence |

## Phase 1: Gates and decisions

- [ ] 1.1 Confirm #660 interface drift none; record result in `design.md` Open Questions — in-repo evidence recorded (no signature drift found via `git log --grep`), full owner confirmation still pending
- [x] 1.2 Obtain DALLAY-565 sign-off on interim `rawToken`/`acceptUrl` debt or redact now; fill `design.md` sign line — approved 2026-09-17, repository owner sign-off recorded in `design.md`
- [ ] 1.3 Coordinate `recordBulkInvite` tag removal with observability owners; locate or inline RFC 12/14/40/43/44 clauses — in-repo evidence recorded (no dashboard/frontend consumer of removed tags found), external owner approval still pending

## Phase 2: Core fixes (TDD: failing test first per fix)

- [x] 2.1 RED bulk aggregate-only test then GREEN `platformadmin/infrastructure/observability/InvitationObservability.kt`: drop per-value tags
- [x] 2.2 RED CAS-false test then GREEN `platformadmin/application/handler/InviteWaitlistEntryHandler.kt`: honor boolean, throw conflict, use `revoke()`
- [x] 2.3 Scope `platformadmin/domain/InvitationIssued.kt`, `DirectInvitationResent.kt` as interim debt (DALLAY-566) with no new fields
- [x] 2.4 Redact or scope `shared/notifications/.../InvitationEmail.kt` `toPayload()` persisted `acceptUrl`; render URL transiently in `SendInvitationEmailConsumer.kt`
- [x] 2.5 Enforce per-key+IP accept-attempt throttle at transport edge reusing `InvitationRateLimitExceededException`; keep coordinator pure

## Phase 3: Security and concurrency evidence

- [x] 3.1 Hash-only test: raw-token lookup returns nothing in `R2dbcInvitationRepositoryTest` (mirror password-reset pattern) — `raw token value matches no stored row`, PR3
- [x] 3.2 Expiry/revoked/consumed/replay rejection tests with safe codes in `InvitationActivationCoordinatorTest` + Postgres integration — EXPIRED/REVOKED/ALREADY_CONSUMED coordinator-level tests added PR3; replay-denied covered by existing `platform-admin.feature` BDD scenario (pre-dates 662, unaffected)
- [x] 3.3 Concurrent exactly-one-winner invitation-layer test (mirror `consumeAndUpdatePassword` pattern) + `platform-admin.feature` replay-denied BDD — confirmed already present (`concurrent acceptance clients allow one success and one membership` in `R2dbcInvitationRepositoryTest`, predates 662, verified unaffected by PR1–PR3 changes)
- [x] 3.4 No-bearer audit/log/metric payload assertions: no raw token, URL, or full email; throttled accept returns safe code — no-bearer assertions from PR2; PR3 adds throttle-bounds-locked test (`accept throttle locks attempt bounds to ten per ten minutes`)

## Phase 4: Reconciliation and gates

- [x] 4.1 Reconcile delta specs with code: no silent contradiction; update `docs/architecture/adr/0020*`, `0021*` refs only by citation — `design.md` and `specs/invitations/spec.md` reconciled to merged 4-series metric shape and throttle bounds, PR3
- [x] 4.2 Run `just backend-check`, `just backend-bdd-fast`, `just backend-test-postgres` where touched; zero new Detekt/baseline/suppressions — Detekt BUILD SUCCESSFUL, Spotless clean, 44/44 touched-scope tests green; full-module `backend-check` surfaced 2 pre-existing flaky failures unrelated to this slice (`LocalAuthEndpointIntegrationTest` timeout, `WaitlistRateLimitIntegrationTest` timing assertion — both outside `platformadmin` invitations, confirmed via isolated reruns)
