# QA Report: fix-invitation-email-delivery

## 1. Identity

| Field | Value |
|---|---|
| Change | `fix-invitation-email-delivery` |
| Mode | `openspec` |
| Phase | `qa` |
| Date | 2026-09-15 |
| Worktree | `/Users/acosta/Dev/dallay/worktrees/fix-invitation-email-delivery` |
| Branch | `feature/fix-invitation-email-delivery-1` |
| Report kind | `fallback` — no repository-specific SDD QA runner/FSM was available; direct Gradle/JUnit/Cucumber commands were used and runtime XML artifacts were inspected |

This report is an acceptance QA audit record. It does not claim acceptance for a deployed product or for the QA harness itself.

## 2. Source artifacts and technical verification handoff

Read before execution:

- `openspec/changes/fix-invitation-email-delivery/proposal.md`
- `openspec/changes/fix-invitation-email-delivery/specs/invitations/spec.md`
- `openspec/changes/fix-invitation-email-delivery/specs/email-notifications/spec.md`
- `openspec/changes/fix-invitation-email-delivery/design.md`
- `openspec/changes/fix-invitation-email-delivery/tasks.md`
- `openspec/changes/fix-invitation-email-delivery/verify-report.md`
- `openspec/changes/fix-invitation-email-delivery/state.yaml`
- `openspec/config.yaml`
- `AGENTS.md` and `.agents/AGENTS.md`
- `apps/web/PRODUCT.md` and `apps/web/app/PRODUCT.md`
- ADR-0002, ADR-0004, ADR-0006, ADR-0016, ADR-0020, and ADR-0021

Technical verification handed off `PASS WITH WARNINGS`. Focused handler/consumer tests were reported as 35/35 passed, focused PostgreSQL invitation/notification integration as 19/19 passed, Detekt/backend-check as passed, and BDD runtime as previously unverified. This QA phase specifically closed the BDD runtime gap with completed fast and PostgreSQL Cucumber result artifacts.

## 3. Target, environment, permissions, and limitations

### Target

The acceptance target is the backend platform-admin direct invitation HTTP surface and its post-commit notification behavior, exercised through the repository's Spring WebFlux `WebTestClient`, Cucumber glue, R2DBC persistence, Testcontainers PostgreSQL, and provider test doubles.

### Environment

- Local macOS worktree only.
- Branch remained `feature/fix-invitation-email-delivery-1`; no branch operation was performed.
- Docker/Testcontainers-backed PostgreSQL executed successfully for the focused and BDD PostgreSQL lanes.
- Test credentials were repository fixtures only: platform operator, auditor, and unauthenticated cases.
- Provider behavior was exercised through test doubles; no external Resend delivery or deployed environment was contacted.
- No CI, preview, staging, production, or deployed evidence was available or claimed.

### Permissions

- Authenticated `PLATFORM_OPERATOR` fixture used for permitted create/resend flows.
- `AUDITOR` fixture used for unauthorized create/resend checks.
- Unauthenticated request fixture used for 401 coverage.

### Limitations

- No browser UI target is in scope for this backend-only change.
- No accessibility, responsive-layout, or browser visual acceptance claim is made.
- No external provider or deployed email receipt was verified.
- The complete PostgreSQL integration task was also run and failed during an unrelated `PublishingWorkerTransactionPostgresIntegrationTest` initialization; the change-focused PostgreSQL tests passed. This remains a suite-level warning.
- The repository's generic SDD quality runner/FSM was unavailable, so the report uses direct-command fallback. Unlike the prior verification attempt, the BDD commands completed and produced JUnit XML artifacts.

## 4. Capability inventory

| Capability | Status | Selection and rationale |
|---|---|---|
| Fast Cucumber/API runtime | available / selected | `just backend-bdd-fast`; rerun with `./gradlew :server:smp:bddFastTest --no-daemon --rerun-tasks ...` to force execution and obtain result artifacts |
| PostgreSQL Cucumber runtime | available / selected | `./gradlew :server:smp:bddPostgresTest --no-daemon --rerun-tasks ...`; required for real persistence and HTTP acceptance evidence |
| Focused PostgreSQL invitation integration | available / selected | `PlatformAdminInvitationTransactionPostgresIntegrationTest` covers commit, rollback, resend identity, provider failure, and persistence effects |
| Focused PostgreSQL notification repository integration | available / selected | `R2dbcNotificationRepositoryPostgresTest` covers notification persistence/idempotency storage |
| Focused unit/runtime handler and consumer tests | available / selected | Handler and consumer behavior was rerun with JUnit XML inspection |
| Backend technical gate | available / selected | `just backend-check` completed successfully, including Detekt and configured backend tests |
| Full PostgreSQL integration suite | available / selected with warning | Run for broader regression signal; failed on unrelated publishing-worker test initialization, not on the invitation-focused tests |
| Static/source inspection | available / rejected as acceptance proof | Used only to map scenarios and locate artifacts; static inspection was not converted into a PASS |
| Browser/UI automation | unavailable for this target / rejected | No browser target or user-facing UI change is part of this backend change |
| Accessibility audit | unavailable / not applicable | No browser-rendered surface changed |
| Responsive/viewport audit | unavailable / not applicable | No browser-rendered surface changed |
| Locale/internationalization runtime | unavailable / not applicable | The contract specifies English backend email copy and does not add locale behavior |
| Deployed/staging acceptance | unavailable | No deployment target or credentials were supplied; no deployed claim is made |
| External email-provider receipt | unavailable | Provider is intentionally mocked/test-doubled; external delivery is outside this local QA target |

## 5. Scenario matrix

All rows below have an explicit QA result. Runtime claims are based on completed Cucumber/JUnit execution, not source inspection alone.

| Scenario | Result | Evidence or reason |
|---|---|---|
| Direct create commits an `ACTIVE` invitation and returns the expected successful response | PASS | Fast and PostgreSQL Cucumber feature `invitations-direct.feature`: 16 tests, 0 failures, 0 errors in each lane; XML: `server/smp/build/test-results/bddFastTest/TEST-feature_classpath_features-platformadmin-invitations-direct.feature.xml` and corresponding `bddPostgresTest` XML. Focused PostgreSQL transaction test also passed. |
| Direct resend commits successfully for an active invitation | PASS | Same fast/PostgreSQL Cucumber feature, `Operator resends a direct invitation`; focused handler and PostgreSQL integration runtime passed. |
| Existing-workspace create/resend uses resolved `workspaces.name`, not workspace ID | PASS | `PlatformAdminInvitationTransactionPostgresIntegrationTest` passed the resolved-name dispatch case; PostgreSQL Cucumber unknown-workspace and direct invitation scenarios also passed. |
| New-workspace create uses the canonical new-workspace copy and no workspace lookup | PASS | Focused PostgreSQL transaction test passed the new-workspace dispatch case; PostgreSQL Cucumber `Operator creates a new-workspace direct invitation` passed. |
| Unknown workspace returns HTTP 404 with `WORKSPACE_NOT_FOUND` and no target-side commit | PASS | Fast and PostgreSQL Cucumber `Creating a direct invitation for an unknown workspace returns 404` passed; focused PostgreSQL transaction test `should write neither invitation nor audit when the direct workspace lookup misses` passed. |
| Initial delivery identity is distinct from intentional resend identity | PASS | Focused PostgreSQL transaction test suite passed the initial and two-resend delivery cases; Cucumber `Resending twice creates distinct deliveries without leaking the token` passed in both runtime lanes. |
| Replay/deduplication reuses an existing notification, including `FAILED`, without another provider call | PASS | Focused PostgreSQL transaction test `should persist FAILED notification while invitation stays ACTIVE and reuse FAILED on replay` passed; notification repository PostgreSQL suite passed 7/7. |
| Provider failure returns HTTP success while notification becomes `FAILED` and invitation remains `ACTIVE` | PASS | Fast and PostgreSQL Cucumber `Provider failure still returns success without leaking the token` passed; focused PostgreSQL transaction test for `FAILED` notification with `ACTIVE` invitation passed. |
| Publication failure rolls back invitation and audit and suppresses notification/provider dispatch | PASS | Focused PostgreSQL transaction test `should roll back invitation and audit without dispatch when publication fails` passed with real R2DBC wiring. No static-only PASS was used. |
| Token is not exposed in create/resend HTTP responses | PASS | Fast and PostgreSQL Cucumber create, new-workspace, resend-redaction, distinct-resend, and provider-failure scenarios passed their response-redaction steps. |
| Unauthorized and unauthenticated access is rejected | PASS | Fast and PostgreSQL Cucumber role/401 scenarios passed: auditor create/resend returns 403 and unauthenticated create returns 401. |
| Invitation state transitions remain observable and safe around resend/revoke/consumed cases | PASS | Fast and PostgreSQL Cucumber direct invitation feature passed the revoke, resent-revoke, consumed-resend 409, duplicate 409, and authorization scenarios. |
| Persistence and post-commit behavior | PASS | Focused PostgreSQL invitation transaction suite passed 12/12 and notification repository suite passed 7/7; BDD PostgreSQL direct feature passed 16/16. |
| Repeated/interrupted delivery behavior | PASS | Intentional repeated resend and failed-event replay are covered by focused PostgreSQL runtime tests and the repeated-resend Cucumber scenario. Crash-durable outbox recovery is explicitly out of scope. |
| Security/token boundary | PASS for observable HTTP redaction; warning retained for known raw-token internal handoff | Cucumber redaction steps passed. The proposal and ADRs explicitly assign token-safe internal handoff follow-up to DALLAY-566; this QA does not reinterpret that out-of-scope limitation. |
| Browser behavior | NOT TESTED | Not applicable to this backend/API-only change; no browser target was supplied. |
| Accessibility | NOT TESTED | Not applicable to this backend/API-only change; no rendered UI changed. |
| Responsive behavior | NOT TESTED | Not applicable to this backend/API-only change; no rendered UI changed. |
| Internationalization/locale behavior | NOT TESTED | No locale capability or locale-specific requirement is in this change; English copy is the specified contract. |
| Deployed/external provider acceptance | NOT TESTED | No deployed target, external provider credentials, or email inbox target supplied. Local provider test doubles are the available evidence. |
| Exploratory acceptance beyond the defined contract | NOT TESTED | No separate manual/exploratory target exists; QA stayed within the change's defined backend acceptance surface. |

## 6. Executed checks and exact results

| Check | Result | Evidence |
|---|---|---|
| `just backend-bdd-fast` first invocation | NOT TESTED as runtime evidence | Initially reused an up-to-date task and did not provide a completed result artifact; it was not treated as BDD acceptance evidence. |
| Forced fast BDD runtime | PASS | `./gradlew :server:smp:bddFastTest --no-daemon --rerun-tasks -x :shared:common:test -x :shared:spring-boot-common:test`, exit 0, `BUILD SUCCESSFUL in 5m 36s`; direct invitation feature JUnit XML: 16/16 passed. Full fast BDD result directory totals: 252 tests, 0 failures, 0 errors, 0 skipped. |
| Forced PostgreSQL BDD runtime | PASS | `./gradlew :server:smp:bddPostgresTest --no-daemon --rerun-tasks -x :shared:common:test -x :shared:spring-boot-common:test`, exit 0, `BUILD SUCCESSFUL in 5m 51s`; direct invitation feature JUnit XML: 16/16 passed. Full PostgreSQL BDD result directory totals: 252 tests, 0 failures, 0 errors, 0 skipped. |
| Focused handler/consumer unit suite | PASS | Create handler 11/11, resend handler 11/11, consumer 13/13; XML reports under `server/smp/build/test-results/test/`; no failures/errors/skips. |
| Focused invitation/notification PostgreSQL integration | PASS | Invitation transaction 12/12 and notification repository 7/7; XML reports under `server/smp/build/test-results/test/`; no failures/errors/skips. |
| Combined focused acceptance PostgreSQL selection | PASS | Invitation transaction + notification repository + selected Cucumber class completed with exit 0 and `BUILD SUCCESSFUL in 1m 57s`; direct BDD artifact was separately verified in the dedicated forced BDD run. |
| `just backend-check` | PASS | Exit 0, `BUILD SUCCESSFUL in 8m 27s`; configured backend tests, Detekt, and Kover verification completed. |
| Complete `postgresIntegrationTest` | FAIL / unrelated suite warning | First full run exceeded the initial 20-minute command timeout while producing no final result; retry completed with `403 tests completed, 1 failed`, failure in `PublishingWorkerTransactionPostgresIntegrationTest` initialization. This is not an invitation-change failure and was not silently ignored. |
| Git/scope inspection | PASS | Branch remained unchanged; no commit/push/rebase/merge performed; no DALLAY-567 QA artifact appears in the changed-file list; no other worktree was modified by this QA execution. |

## 7. Untested scope, reason, and rerun prerequisite

| Scope | Reason | Rerun prerequisite |
|---|---|---|
| Browser/UI, accessibility, responsive | Backend-only change with no browser target | Supply a running UI target and applicable browser/a11y capability if the scope expands |
| Deployed provider/inbox receipt | No deployed target or provider credentials; provider guarantees beyond `PENDING`/`SENT`/`FAILED` are out of scope | Supply approved staging/deployed endpoint and provider/inbox test arrangement |
| Crash-durable outbox/recovery | Explicitly out of scope in proposal/spec | New approved change and implementation phase |
| Token-safe internal event handoff | Explicitly assigned to DALLAY-566; HTTP response redaction was tested | DALLAY-566 implementation and its own QA; do not edit DALLAY-567 artifacts here |
| Independent exploratory QA | No separate manual target beyond deterministic backend harness | Supply an exploratory environment or operator runbook |
| Full PostgreSQL suite green | Unrelated publishing-worker initialization failure | Resolve or quarantine the unrelated publishing-worker test failure in a separate apply/verification phase, then rerun `postgresIntegrationTest` |

## 8. Findings

| ID | Severity | Status | Finding |
|---|---|---|---|
| QA-001 | P2 | OPEN — unrelated | Full `postgresIntegrationTest` is not green because `PublishingWorkerTransactionPostgresIntegrationTest` fails during initialization (`403 tests completed, 1 failed`). The invitation-focused PostgreSQL tests and BDD PostgreSQL suite passed. This is a suite-level warning, not evidence of a defect in this change. |
| QA-002 | P2 | DOCUMENTED / accepted by scope | The raw-token handler → event → consumer handoff remains temporarily present, with token-safe follow-up assigned to DALLAY-566. HTTP responses are redacted and all requested redaction scenarios passed; internal handoff hardening is not silently claimed as complete. |

No `CRITICAL`, `P0`, or `P1` finding was identified for this change's acceptance surface. QA did not modify source code or fix findings.

## 9. Final verdict

**PASS WITH WARNINGS**

### Rationale

The previously missing BDD runtime evidence is now present: both fast and PostgreSQL Cucumber executions completed successfully, and the direct invitation feature produced auditable JUnit XML with 16/16 scenarios passing in each lane. The focused real-PostgreSQL integration evidence also covers the requested commit/rollback, existing/new workspace behavior, distinct resend identity and replay dedupe, provider failure (`HTTP success` plus `FAILED` notification and `ACTIVE` invitation), missing-workspace 404, publication-failure suppression, and token-redaction behavior. `just backend-check` passed.

The verdict remains `PASS WITH WARNINGS`, rather than unconditional `PASS`, because the complete PostgreSQL integration task has an unrelated publishing-worker initialization failure and the proposal documents a token-safe internal handoff follow-up outside this change. These warnings do not invalidate the change-focused acceptance evidence, but the full suite issue should be resolved separately before treating the repository as globally green.

## 10. Implementation handoff

- No production code was changed during QA.
- Do not start a corrective apply phase for the invitation flow based on this report; the change-focused acceptance scenarios passed.
- Track `QA-001` as a separate test-suite maintenance issue and rerun the full PostgreSQL integration task after that issue is addressed.
- Preserve the DALLAY-566 token-safe follow-up boundary and do not edit DALLAY-567 QA artifacts.
- Archive may proceed only under the repository policy's explicit `PASS WITH WARNINGS` and unrelated-suite-warning handling; it must retain this report and the existing `verify-report.md`.
