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

- [ ] 1.1 Confirm #660 interface drift none; record result in `design.md` Open Questions
- [ ] 1.2 Obtain DALLAY-565 sign-off on interim `rawToken`/`acceptUrl` debt or redact now; fill `design.md` sign line
- [ ] 1.3 Coordinate `recordBulkInvite` tag removal with observability owners; locate or inline RFC 12/14/40/43/44 clauses

## Phase 2: Core fixes (TDD: failing test first per fix)

- [x] 2.1 RED bulk aggregate-only test then GREEN `platformadmin/infrastructure/observability/InvitationObservability.kt`: drop per-value tags
- [x] 2.2 RED CAS-false test then GREEN `platformadmin/application/handler/InviteWaitlistEntryHandler.kt`: honor boolean, throw conflict, use `revoke()`
- [ ] 2.3 Scope `platformadmin/domain/InvitationIssued.kt`, `DirectInvitationResent.kt` as interim debt (DALLAY-566) with no new fields
- [ ] 2.4 Redact or scope `shared/notifications/.../InvitationEmail.kt` `toPayload()` persisted `acceptUrl`; render URL transiently in `SendInvitationEmailConsumer.kt`
- [ ] 2.5 Enforce per-key+IP accept-attempt throttle at transport edge reusing `InvitationRateLimitExceededException`; keep coordinator pure

## Phase 3: Security and concurrency evidence

- [ ] 3.1 Hash-only test: raw-token lookup returns nothing in `R2dbcInvitationRepositoryTest` (mirror password-reset pattern)
- [ ] 3.2 Expiry/revoked/consumed/replay rejection tests with safe codes in `InvitationActivationCoordinatorTest` + Postgres integration
- [ ] 3.3 Concurrent exactly-one-winner invitation-layer test (mirror `consumeAndUpdatePassword` pattern) + `platform-admin.feature` replay-denied BDD
- [ ] 3.4 No-bearer audit/log/metric payload assertions: no raw token, URL, or full email; throttled accept returns safe code

## Phase 4: Reconciliation and gates

- [ ] 4.1 Reconcile delta specs with code: no silent contradiction; update `docs/architecture/adr/0020*`, `0021*` refs only by citation
- [ ] 4.2 Run `just backend-check`, `just backend-bdd-fast`, `just backend-test-postgres` where touched; zero new Detekt/baseline/suppressions
