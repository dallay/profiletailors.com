# Acceptance QA Report: dallay-561-registration-policy

## Identity
- Change: dallay-561-registration-policy (Linear DALLAY-561, Done since 2026-08-27, PR #873 "feat: Add typed registration policy modes")
- Mode: openspec
- QA phase: qa (lifecycle `apply → verify → qa → archive`, after `sdd-verify`, before `sdd-archive`)
- Date: 2026-09-09

## Sources of Truth
- Proposal: `openspec/changes/dallay-561-registration-policy/proposal.md` — one mutually exclusive runtime policy (`OPEN` / `INVITE_ONLY` / `CLOSED`), evaluated before any mutation, typed non-secret config with fail-closed default, public capability advertises public registration only in `OPEN`.
- Specifications: `openspec/changes/dallay-561-registration-policy/spec.md` (delta: mutually exclusive policy, typed config, public capability) and `openspec/specs/registration/spec.md` (includes the DALLAY-561 requirements plus preserved atomic-registration/session behavior).
- Design: `openspec/changes/dallay-561-registration-policy/design.md` — `RegistrationMode` enum + `RegistrationPolicy`/`RegistrationDecision` port, handler evaluates with no validated invitation context, `SMP_REGISTRATION_MODE` default `CLOSED`.
- Tasks: `openspec/changes/dallay-561-registration-policy/tasks.md` — all phases checked complete.
- Technical verification: `openspec/changes/dallay-561-registration-policy/verify-report.md` — **PASS WITH WARNINGS** (typed decisions, handler evaluates before normalization/persistence/events/session, 403 `REGISTRATION_INVITATION_REQUIRED` with no work, CLOSED preserves unavailable problem, two-field public capability, `SMP_REGISTRATION_MODE` wiring; warnings: production `.env` absent, swarm needs explicit image/origin vars, no remote CI/deployment acceptance, direct invite-only stays rejected until DALLAY-567).
- State: `state.yaml` — `current_phase: verify`, completed through `verify`, `next: user-review`. No `qa-report.md` existed before this run.

Execution mode: direct (`fallback` — no runner envelope or FSM was used; evidence is Gradle console output plus JUnit/Cucumber XML under `server/smp/build/test-results/`). QA modified no source code; working tree was clean (`git status --short` empty) for the entire run.

## Target and Environment
- Target: SMP backend identity slice — `POST /api/auth/register` policy gate, `GET /api/capabilities/public` allow-listed contract, `SMP_REGISTRATION_MODE` binding (`server/smp/src/main/resources/application.yaml` defaults `CLOSED`; `infra/apps/smp/production/compose.yaml` passes the mode variable). Implementation: `RegistrationMode`, `RegistrationDecision`, `RegistrationPolicy`, `PropertyBackedRegistrationPolicy`, `RegisterUserHandler`, `GetPublicCapabilitiesHandler`.
- Environment: macOS worktree `/Users/acosta/Dev/dallay/worktrees/clean-specs`, Docker 29.0.1 available (Testcontainers PostgreSQL used by BDD), JDK/Gradle via wrapper with configuration cache.
- Credentials/permissions: none required; BDD uses repository fixture tokens (`valid-token` family), no real credentials.
- Limitations: no running deployment target (no dev server booted, no production `.env`); operator/config acceptance rests on unit/config tests plus verify's example-value compose renders. DALLAY-567 (invitation acceptance) and DALLAY-576 (Back Office exposure) are out of scope — this slice only is validated.

## Capability Inventory
| Capability | Availability | Selected? | Rationale / rejection reason |
|---|---|---|---|
| backend_unit (JUnit 5: domain, application, config, HTTP problem mapping) | available | Yes | Narrowest capability with observable pass/fail for policy decisions, handler zero-work, binding, and contract mapping. |
| backend_bdd_fast (Cucumber on JUnit Platform + Testcontainers PostgreSQL) | available | Yes | HTTP-level acceptance evidence incl. the invite-only denial scenario and capability contract, backed by real PostgreSQL. |
| backend_bdd_postgres (Postgres BDD variant) | available | No | Not re-run in QA: identical local-auth/registration scenarios already executed in the fast lane on Testcontainers PostgreSQL; verify-report records this lane PASS. Re-run prerequisite: `just backend-bdd-postgres` (plus `just infra-up` if required). |
| backend_postgres_integration (Testcontainers integration incl. registration transaction test) | available | No | Not re-run in QA; verify-report records `just backend-check` PASS including Postgres integration. Re-run prerequisite: `just backend-test-postgres`. |
| API manual/client requests against a live server | unavailable | No | No deployed or dev-server target exists in this checkout; BDD's WebTestClient coverage is the narrower executable equivalent. |
| browser / Playwright E2E | rejected | No | No UI surface changed in this slice; public capability response shape is unchanged (still `registrationEnabled` boolean). |
| accessibility / responsive | rejected | No | Non-applicable: no frontend markup, styling, or interaction changed. |
| locale / i18n | rejected | No | Non-applicable: no user-facing copy or routing changed. |
| exploratory / manual session | unavailable | No | No deployed target to explore; blocked on a running environment, not on credentials. |
| compose/swarm config render | available | No | Owned by verify (PASS with example values); production `.env` absent in this checkout. Re-run prerequisite: production `.env` plus explicit image/origin vars, then `just production-config` / `just swarm-config`. |

## Scenario Matrix
| ID | Capability | Acceptance scenario | Result | Evidence or reason |
|---|---|---|---|---|
| QA-01 | backend_bdd_fast + backend_unit | Happy path: OPEN mode proceeds with existing registration transaction and session behavior | PASS | BDD `local-auth.feature` 18/18 incl. "Registration creates an unverified user with session tokens"; `auth/registration.feature` 6/6; `LocalAuthHandlersTest` 26/26. Fresh run 2026-09-09: `bddFastTest` 235/235, 0 failures. |
| QA-02 | backend_bdd_fast + backend_unit | Negative: INVITE_ONLY direct registration without validated invitation is rejected 403 with safe invitation-required error and zero mutation/side effects | PASS | BDD "Invite-only registration rejects before mutation" passed (403 + `REGISTRATION_INVITATION_REQUIRED`); `IdentityProblemDetailsHandlerTest` 15/15; handler zero-work covered in `LocalAuthHandlersTest` 26/26. |
| QA-03 | backend_bdd_fast | Negative: CLOSED mode rejects public registration as unavailable with zero mutation/side effects | PASS | BDD "Disabled registration rejects before mutation" passed (503 + `REGISTRATION_DISABLED`). |
| QA-04 | backend_unit | Centralized reusable evaluation: registration and public-capability paths consume the same policy so advertised and enforced state cannot drift | PASS | `RegistrationModeTest` 3/3; `PublicCapabilitiesHandlerTest` 1/1; `PublicCapabilitiesControllerTest` 4/4; BDD capability scenarios ("expose exactly authoritative…", "expose disabled…") both passed. |
| QA-05 | backend_unit + backend_bdd_fast | Linear AC "automated tests cover all three modes" | PASS | All three modes covered at domain (`RegistrationModeTest`), application (`LocalAuthHandlersTest`), HTTP (`LocalAuthControllerTest` 7/7, problem-details 15/15), config (2/2), and BDD (open/invite-only/closed scenarios) levels. Focused re-run 2026-09-09: 58/58, 0 failures. |
| QA-06 | backend_unit | Boundary/config: missing `SMP_REGISTRATION_MODE` fails closed to CLOSED | PASS | `RegistrationConfigurationPropertiesTest` 2/2 (missing → CLOSED, explicit `INVITE_ONLY` binds). |
| QA-07 | backend_unit | Boundary/config: explicit mode value binds | PASS | Same class as QA-06, explicit `INVITE_ONLY` case passed. |
| QA-08 | backend_unit + backend_bdd_fast | Public capability reports `registrationEnabled: true` only for OPEN, `false` for INVITE_ONLY/CLOSED, two-field response, no extra operational state | PASS | `PublicCapabilitiesControllerTest` 4/4; BDD exact allow-listed contract scenarios passed. |
| QA-09 | backend_bdd_fast | Security/unauthorized: unauthenticated visitor direct POST is denied without side effects in restricted modes | PASS | BDD visitor steps exercise the HTTP layer unauthenticated via WebTestClient; restricted-mode scenarios assert rejection codes with no mutation. |
| QA-10 | backend_bdd_fast | Repeated/state: duplicate registration returns 409, email normalization preserved in OPEN | PASS | `auth/registration.feature` 6/6 (duplicate 409 `USER_ALREADY_EXISTS`, lowercase normalization, workspaceId, ASVS password boundaries). |
| QA-11 | — | Boundary: invalid (non-enum) `SMP_REGISTRATION_MODE` value | NOT TESTED | No test or live boot exercised an invalid value; expected Spring bind failure at startup (fail-closed by not starting) is unconfirmed. Re-run prerequisite: set an invalid value, boot the app, observe startup behavior. |
| QA-12 | browser/a11y/responsive/locale | UI, accessibility, responsive, internationalization behavior | NOT TESTED | Non-applicable: this slice changes no UI surface, markup, styling, copy, or routes (capability shape unchanged). |
| QA-13 | — | Live deployment operator check (production compose/swarm with real `.env`) | NOT TESTED | Production `.env` absent in this checkout; verify validated renders with example values only. Re-run prerequisite: production `.env` + explicit image/origin vars, then `just production-config` / `just swarm-config`. |
| QA-14 | — | Invitation acceptance flow (DALLAY-567 follow-up) | NOT TESTED | Out of scope by design; blocked follow-up builds on this slice. Current-slice behavior (direct invite-only stays rejected) confirmed by QA-02. |
| QA-15 | backend_bdd_fast | Full fast BDD suite stays green (no cross-feature regression) | PASS | Fresh full-suite re-runs 2026-09-09: first run 234/235 with one transient, unrelated timeout (see F-1); immediate rerun 235/235, 0 failures/errors/skips, with zero code changes between runs. |

Exact commands executed (all in `/Users/acosta/Dev/dallay/worktrees/clean-specs`, tree clean throughout):
1. `./gradlew -p server/smp test --tests "…RegistrationModeTest" --tests "…RegistrationConfigurationPropertiesTest" --tests "…PublicCapabilitiesHandlerTest" --tests "…PublicCapabilitiesControllerTest" --tests "…LocalAuthControllerTest" --tests "…IdentityProblemDetailsHandlerTest" --tests "…LocalAuthHandlersTest" --rerun-tasks` → **BUILD SUCCESSFUL**; XML totals: 58 tests, 0 failures, 0 errors, 0 skipped.
2. `just backend-bdd-fast` → **BUILD SUCCESSFUL** (up-to-date; prior 10:56 run: 235 tests, 0 failures).
3. `./gradlew -p server/smp bddFastTest --no-build-cache --rerun-tasks -x detekt -x spotlessCheck` → **BUILD FAILED in 9m 42s**, 234/235; single failure `bulk-scheduling.feature` → "1000-row batch chunked" → `IllegalStateException: Timeout on blocking read for 15000000000 NANOSECONDS` at `BulkBddSteps.postBulkSchedule` (unrelated scheduling domain, load-induced).
4. `CUCUMBER_FEATURES="classpath:features/bulk-scheduling.feature" ./gradlew -p server/smp bddFastTest --no-build-cache` (filter not honored by the suite runner; full suite re-executed) → **BUILD SUCCESSFUL in 8m 8s**; XML totals: 36 files, 235 tests, 0 failures, 0 errors, 0 skipped — transient failure cleared with no code change.

## Untested Scope
- Scope: Invalid `SMP_REGISTRATION_MODE` value handling; live production compose/swarm operator check; Postgres BDD variant and Postgres integration lanes re-run inside QA; browser/a11y/responsive/locale/exploratory behavior.
- Reason: No invalid-value test or live target exists in this checkout; variant lanes already passed under verify and the fast lane re-covers the same scenarios on Testcontainers PostgreSQL; UI categories are non-applicable (backend-only slice, unchanged capability shape).
- Re-run prerequisite: For invalid values — set a bogus mode and boot the app. For operator acceptance — provide production `.env` plus explicit image/origin vars and run `just production-config` / `just swarm-config` (and `just backend-bdd-postgres` / `just backend-test-postgres` for the variant lanes).

## Findings
| ID | Severity | Scenario / location | Evidence | Status |
|---|---|---|---|---|
| F-1 | P3 | `bulk-scheduling.feature` → "1000-row batch chunked" — 15 s WebTestClient blocking-read timeout on one fresh full-suite run | `server/smp/build/test-results/bddFastTest/TEST-feature_classpath_features-bulk-scheduling.feature.xml` (run 1: 1 failure / 235); stack at `BulkBddSteps.postBulkSchedule (BulkBddSteps.kt:85)` | Closed — transient: immediate full-suite rerun green (235/235) with zero code changes; bulk-scheduling domain untouched by this identity-only change; no action for this slice. Watch item for suite-load flakiness only. |

No `CRITICAL`, `P0`, `P1`, or `P2` findings. No acceptance scenario failed.

## Verdict
`PASS WITH WARNINGS`

### Rationale
Every applicable acceptance scenario (QA-01–QA-10, QA-15) passes with fresh observable evidence from this QA run: 58/58 focused registration unit/application/config/HTTP tests and 235/235 fast BDD scenarios on Testcontainers PostgreSQL, including the Gherkin-mandated open-proceeds / invite-only-rejected / closed-rejects behaviors, centralized policy evaluation, fail-closed binding, and the allow-listed public capability. Warnings carried, none blocking: (1) no live deployment target exists, so operator/config acceptance rests on config unit tests plus verify's example-value compose renders; (2) the Postgres BDD/integration variant lanes were not re-run in QA and rest on the verify handoff (the re-run fast lane covers the same scenarios on real PostgreSQL); (3) one transient, unrelated bulk-scheduling timeout was observed and cleared on immediate rerun (F-1, P3, closed).

## Limitations and Handoff
- QA does not fix code.
- Product acceptance is not claimed without a target and observable evidence.
- Follow-up for implementation:
  - No code changes requested by QA; nothing to fix for this slice.
  - Keep DALLAY-567 (invitation acceptance) and DALLAY-576 (Back Office exposure) as the sequenced follow-ups; direct invite-only registration intentionally remains rejected until DALLAY-567.
  - Optional hardening (not required for this slice): cover the invalid-`SMP_REGISTRATION_MODE` startup behavior with a test, and keep an eye on full-suite-load flakiness of the 1000-row bulk-scheduling scenario (F-1).
  - Archive gate input: `verify-report.md` (PASS WITH WARNINGS) and this `qa-report.md` both exist; no unresolved CRITICAL/P0/P1; NOT TESTED items are non-applicable UI categories, the out-of-scope DALLAY-567 flow, or environment-constrained operator/variant lanes with explicit rerun prerequisites above.
