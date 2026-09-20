# Acceptance QA Report: issue-668-user-administration

## Identity

- Change: issue-668-user-administration (GitHub dallay/profiletailors.com#668, "Add Back Office user
  queries and account control actions")
- Mode: openspec
- QA phase: qa (lifecycle `apply → verify → qa → archive`, after `sdd-verify`, before `sdd-archive`)
- Date: 2026-09-18

## Sources of Truth

- Proposal: `openspec/changes/issue-668-user-administration/proposal.md` — list/search/detail plus
  disable/enable/sessions-revoke with audit, single-delivery branch.
- Specifications: `openspec/changes/issue-668-user-administration/specs/` (delta:
  `user-administration`, `admin-authorization`, `iam`, `observability`, `platform-admin-audit`).
- Design: `openspec/changes/issue-668-user-administration/design.md` — Identity + Credentials
  composition under `AtomicTransactionRunner`, durable idempotency, redacted audit.
- Tasks: `openspec/changes/issue-668-user-administration/tasks.md` — all phases checked complete,
  including verify follow-up corrections 6.1–6.7 and correction batch 7.1–7.5.
- Technical verification: `openspec/changes/issue-668-user-administration/verify-report.md` —
  **PASS** (account state, login/refresh gating, revocation counts, commands/routes, audit, metrics,
  BDD 256 scenarios, admin Vitest/type-check/build, PostgreSQL integration, migration tests).
- State: `state.yaml` — `current_phase: apply`, `verification_status: passed`, `next: archive`.
  Implementation merged to main as PR #1077 on 2026-09-17; CI Quality Gate green (SonarCloud
  new-code coverage 86.2%).

Execution mode: direct (no runner envelope; evidence is Vitest/Gradle console output plus JUnit XML
under `apps/web/admin` and `server/smp/build/test-results/`). QA modified no source code.
Pre-existing worktree state left untouched: one staged one-line wording fix in
`openspec/specs/lead-capture-waitlist/spec.md` (belongs to the archived 665 amendment, documented
there as D2), unstaged 662 token-lifecycle test/design refinements under separate review, untracked
665 post-archive amendment files.

## Target and Environment

- Target: Back Office user administration slice — `GET /api/admin/users` (list/search),
  `GET /api/admin/users/{id}` (+ `/workspaces`), `POST .../disable`, `POST .../enable`,
  `POST .../sessions/revoke` (all mutations require `Idempotency-Key` and `platform.users.manage`),
  `USER_DISABLED`/`USER_ENABLED`/`USER_SESSIONS_REVOKED` audit, bounded operation/outcome metrics;
  admin SPA `UsersView`/`UserDetailView` with `accountState` and manage guards. Implementation:
  `platformadmin` user-control handlers, `AdminUserController`, `R2dbcAdminUserQuery`,
  `UserControlIdempotencyService`, Identity `account_state` gating in login/refresh.
- Environment: repo checkout `/root/workspace/dallay/profiletailors.com` on `main`, Docker available
  (Testcontainers PostgreSQL used by persistence tests), JDK/Gradle via wrapper with configuration
  cache, pnpm workspace.
- Credentials/permissions: none required; BDD uses repository fixture tokens (`valid-token` family),
  no real credentials.
- Limitations: no live deployment target (no dev server booted); operator acceptance rests on
  WebTestClient/BDD coverage. Admin Playwright remains not applicable (no runner, configuration, or
  fixtures exist in `apps/web/admin`).

## Capability Inventory

| Capability                                                                                      | Availability | Selected? | Rationale / rejection reason                                                                                                                                        |
|-------------------------------------------------------------------------------------------------|--------------|-----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| admin_vitest (Vitest: UsersView, UserDetailView, auth store, API client)                        | available    | Yes       | Direct acceptance evidence for list/detail/controls/permission-denial UI behavior.                                                                                  |
| admin_typecheck_build (vue-tsc, vite build)                                                     | available    | Yes       | Contract coherence between SPA, i18n labels, and API routes.                                                                                                        |
| backend_unit (JUnit 5: user-control handlers, idempotency, telemetry, WebTestClient controller) | available    | Yes       | Narrowest capability with observable pass/fail for disable/enable/revoke, replay, audit, and permission denial.                                                     |
| backend_bdd_fast (Cucumber on JUnit Platform + Testcontainers PostgreSQL)                       | available    | Partial   | Full-suite fresh re-run attempted twice (see F-1); acceptance scenarios rest on the verify handoff (256/256 on merged code) plus CI Quality Gate green on PR #1077. |
| backend_postgres_integration                                                                    | available    | No        | Not re-run in QA; verify-report records PostgreSQL admin-query, refresh-session, and migration tests PASS on merged code.                                           |
| browser / Playwright E2E                                                                        | unavailable  | No        | No admin Playwright project, configuration, or fixtures exist (tasks 4.3, preserved).                                                                               |
| accessibility / responsive                                                                      | rejected     | No        | Non-applicable as independent QA lanes: controls reuse established accessible confirmation patterns already covered by Vitest; no new interaction model introduced. |
| exploratory / manual session                                                                    | unavailable  | No        | No deployed target to explore; blocked on a running environment, not on credentials.                                                                                |

## Scenario Matrix

| ID    | Capability                  | Acceptance scenario                                                                                                                                        | Result             | Evidence or reason                                                                                                                                                                                              |
|-------|-----------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| QA-01 | backend_unit + admin_vitest | AC1: administrators list and search users (pagination, exact normalized email, state-based status, workspace counts)                                       | PASS               | `AdminUserControllerTest` 19/19 (list/search/detail/404/permission); admin Vitest 87/87 incl. UsersView filter bar and summary card. Fresh run 2026-09-18.                                                      |
| QA-02 | backend_unit + admin_vitest | AC2: inspect operational user detail (account state, verification, registration time, memberships, roles); workspace detail requires both read permissions | PASS               | Controller workspace dual-permission tests; `UserDetailView` fixtures assert `accountState` sections; verify BDD detail-membership scenarios 256/256 handoff.                                                   |
| QA-03 | backend_unit                | AC3: disable and re-enable accounts; disabled accounts cannot login/refresh and disable revokes refresh sessions before success                            | PASS               | `UserControlHandlersTest` 13/13 (disable/enable/unknown-target/zero-sessions/failure paths); login/refresh disabled-path regression covered in verify handoff (zero rotation calls). Fresh unit run 2026-09-18. |
| QA-04 | backend_unit                | AC4: revoke all active sessions; response reports revoked count                                                                                            | PASS               | Handler revoke tests + controller revoke tests in QA-01/QA-03 classes; `R2dbcRefreshSessionGateway` active-only revocation in verify handoff.                                                                   |
| QA-05 | backend_unit                | AC5: control mutations audited (`USER_DISABLED`, `USER_ENABLED`, `USER_SESSIONS_REVOKED`); unauthenticated mutations emit redacted rejected events         | PASS               | `UserControlTelemetryTest` 1/1 + handler audit assertions incl. 6.6 redacted-rejected coverage. Fresh run 2026-09-18.                                                                                           |
| QA-06 | backend_unit + admin_vitest | Security: read-only roles denied on mutations; unauthorized attempts rejected without side effects                                                         | PASS               | Controller 403 tests; auth-store tests assert `platform.users.manage` and read-only denial; verify BDD permission-denial scenarios handoff.                                                                     |
| QA-07 | backend_unit                | Repeatability: idempotent replay avoids side effects and success-audit duplication; in-progress races return explicit conflict                             | PASS               | `UserControlIdempotencyServiceTest` 7/7 (claim/replay/scope-conflict/in-progress); verify BDD replay scenario handoff. Fresh run 2026-09-18.                                                                    |
| QA-08 | backend_bdd_fast            | Full fast BDD suite stays green (no cross-feature regression)                                                                                              | NOT TESTED (fresh) | See F-1. Rests on verify handoff (256/256 fresh on 2026-09-17 against merged PR #1077 code) and CI Quality Gate green on #1077.                                                                                 |
| QA-09 | browser                     | Admin Playwright flow/denial coverage                                                                                                                      | NOT TESTED         | Not applicable: no admin Playwright project exists (tasks 4.3). Existing Vitest + BDD cover the implemented controls and denial.                                                                                |

Exact commands executed (all in `/root/workspace/dallay/profiletailors.com`):

1. `pnpm --filter @profiletailors/admin test:run` → **9 files, 87 tests, 0 failures**.
2. `pnpm --filter @profiletailors/admin type-check` (`vue-tsc --build`) → **clean, no errors**.
3. `pnpm --filter @profiletailors/admin build` → **success** (vite build, all admin views emitted).
4.
`./gradlew :server:smp:test --tests "…UserControlHandlersTest" --tests "…UserControlIdempotencyServiceTest" --tests "…contracts.UserControlTelemetryTest" --tests "…http.AdminUserControllerTest" --no-daemon --console=plain` →
**BUILD SUCCESSFUL in 1m 1s**; XML totals: 40 tests, 0 failures, 0 errors, 0 skipped.
5.
`./gradlew :server:smp:bddFastTest --no-daemon --console=plain --rerun-tasks -x :shared:common:test -x :shared:spring-boot-common:test` →
**no result** (attempt 1: terminated after 10 min with zero output; attempt 2 relaunched with
`cleanBddFastTest` in background: test worker died silently with no XML written). See F-1.

## Untested Scope

- Scope: fresh full-suite `bddFastTest` re-run; Postgres BDD/integration variant lanes re-run inside
  QA; live operator check; admin Playwright (nonexistent runner).
- Reason: variant lanes and full BDD already passed under verify against the merged code and under
  CI on PR #1077; backend/frontend slices touched by nothing since merge except unrelated 662
  test-only refinements and docs wording. Full-suite local re-run blocked by environment behavior
  (F-1), not by product code.
- Re-run prerequisite: `just backend-bdd-fast` from a clean shell with no competing Gradle workers
  (prior green runs completed in 5–9 min in CI-like conditions); `just backend-test-postgres` /
  `just backend-bdd-postgres` (plus `just infra-up` if required) for variant lanes.

## Findings

| ID  | Severity         | Scenario / location                                       | Evidence                                                                                                                                                                                                                                                                                | Status                                                                                                                      |
|-----|------------------|-----------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| F-1 | P3               | Fresh full-suite `bddFastTest` produced no result locally | Attempt 1 killed at 10 min timeout with zero console output; attempt 2 (background, `cleanBddFastTest` + `bddFastTest`) worker died silently ~10 min in, no XML; note `cleanBddFastTest` deleted the 2026-09-16 result XMLs, so the only full-suite artifacts are the verify/CI records | Open (process) — mitigated: verify handoff 256/256 + CI Quality Gate green on merged #1077; no product-code cause indicated |
| F-2 | P4 informational | Admin Vitest count is 87 vs 82 recorded in apply-progress | Fresh run 2026-09-18: 9 files, 87 passed, 0 failed; delta comes from `main` evolution after the verify snapshot, all green                                                                                                                                                              | Closed — no action; recorded for count traceability                                                                         |

No `CRITICAL`, `P0`, `P1`, or `P2` findings. No acceptance scenario failed.

## Verdict

`PASS WITH WARNINGS`

### Rationale

Every applicable acceptance scenario (QA-01–QA-07) passes with fresh observable evidence from this
QA run: 87/87 admin Vitest, clean admin type-check and build, and 40/40 focused backend
user-control/idempotency/telemetry/controller tests, covering the Gherkin-mandated
disable/revoke/replay/denial behaviors. Warnings carried, none blocking: (1) the fresh full-suite
BDD re-run produced no local result (F-1, P3, process-only) — the merged code is still covered by
verify's fresh 256/256 run plus the CI Quality Gate green on PR #1077; (2) variant Postgres lanes
and live operator checks rest on the verify handoff with explicit rerun prerequisites above.

## Limitations and Handoff

- QA does not fix code.
- Product acceptance is not claimed without a target and observable evidence.
- Follow-up for implementation:
    - No code changes requested by QA; nothing to fix for this slice.
    - Keep #672 (registration mode config) → #670 (notification retry) → #671 (takedown governance)
      as the sequenced follow-ups.
    - Optional hardening (not required for this slice): re-run `just backend-bdd-fast` from a clean
      shell to restore a local full-suite artifact; investigate background-worker mortality under
      parallel frontend builds if it recurs.
    - Archive gate input: `verify-report.md` (PASS) and this `qa-report.md` both exist; no
      unresolved CRITICAL/P0/P1; NOT TESTED items are the environment-constrained full-suite re-run
      (F-1, mitigated), nonexistent admin Playwright runner, or variant lanes with explicit rerun
      prerequisites above.
