# Verification Report

**Change**: `dallay-567-accept-invitations-registration-flow`
**Version**: N/A
**Mode**: `openspec`
**Date**: `2026-09-10`
**Branch**: `feat/dallay-567-invitation-evidence`
**HEAD**: `d4b461f8` (`Merge branch 'main' into feat/dallay-567-invitation-evidence`)
**Base**: `main` / `0b551351`
**Verification mode**: `fallback` — `openspec/quality-runner.json`, `scripts/sdd-quality-runner.mjs`, and the supplemental strict-TDD verifier are unavailable. Direct command evidence is recorded; no unavailable runner status is treated as a pass.

## Completeness

| Metric | Value |
|---|---:|
| Tasks in `tasks.md` | 12 |
| Tasks complete | 12 |
| Tasks incomplete | 0 |
| Follow-up scope | Test and fixture evidence only |

The current `tasks.md` contains twelve checked items. The resolved delivery strategy is `single-pr`, with medium 400-line budget risk and no chained PR recommendation. The worktree remains intentionally dirty because the DALLAY-567 implementation and follow-up artifacts are not committed on top of the merge commit.

## Branch and Production-Scope Reconciliation

| Check | Result | Evidence |
|---|---|---|
| Current main was merged | ✅ PASS | `HEAD` is merge commit `d4b461f8`; its main parent is `0b551351`, which is also the current `main`/`origin/main` tip. |
| QA-06..QA-10 follow-up is test-only | ✅ PASS | Follow-up paths are Cucumber feature files/glue/database assertions and `apps/web/app/e2e/specs/invitee-private-beta.spec.ts`; all are test or test-fixture paths. `apply-progress.md` records no new production-code, API-contract, classifier, copy, or runtime-configuration changes. |
| Aggregate worktree is production-clean | ⚠️ NOT CLAIMED | `git status` shows 50 entries, including pre-existing DALLAY-567 production implementation paths and the prior scheduler-sidebar fix. Those changes are preserved baseline work, not attributed to the QA-06..QA-10 follow-up. |
| Follow-up changes alter existing behavior | ✅ NO EVIDENCE | Source inspection and the follow-up path diff show assertions, fixtures, route mocks, and database counts only. |
| Whitespace integrity | ✅ PASS | `git diff --check` passed after the test runs. |

The production-scope conclusion is deliberately narrow: the QA-06..QA-10 follow-up did not add production behavior. It does not claim that the entire dirty worktree contains no production changes.

## Build and Test Evidence

| Command | Result | Exact evidence |
|---|---|---|
| `just worktree-check` | ✅ PASS | 5 tests passed, 0 failed, 0 skipped. |
| `git diff --check` | ✅ PASS | No whitespace errors. |
| `pnpm --filter app test:run` | ✅ PASS | 147 test files; 1,729 tests passed. |
| `pnpm --filter app lint` | ✅ PASS | Biome checked 830 files with no errors. |
| `pnpm --filter app type-check` | ✅ PASS | `vue-tsc --build` completed without errors. |
| `pnpm --filter app build` | ✅ PASS WITH ADVISORY | Vite build completed; only the existing non-blocking chunk-size advisory was emitted. |
| `just backend-bdd-fast` | ⚠️ RETRIED PASS | The 300-second attempt was terminated by the harness with signal 15. The retry with a 600-second timeout completed `BUILD SUCCESSFUL` in 4m 52s. The feature inventory is 24 local-auth scenarios and 20 platform-admin scenarios. |
| `just infra-up` + `just backend-bdd-postgres` + `just infra-down` | ✅ PASS | PostgreSQL BDD completed `BUILD SUCCESSFUL` in 4m 48s; temporary PostgreSQL, Mailpit, and WireMock services were stopped. |
| Invitation Playwright command from `tasks.md` | ✅ PASS | `36 passed` across Chromium, Firefox, and Mobile Chrome; 36 tests ran. |
| Scheduler-filtering Playwright command | ✅ PASS | `9 passed` across Chromium, Firefox, and Mobile Chrome. |
| `just backend-check` | ✅ PASS | `:server:smp:check` completed `BUILD SUCCESSFUL` in 11m 09s, including backend tests, PostgreSQL integration tests, Detekt, Spotless, security-version verification, and Kover verification. |
| `just backend-coverage` | ✅ PASS | Repository coverage recipe completed `BUILD SUCCESSFUL` and generated the JaCoCo HTML report at `server/smp/build/reports/jacoco/test/html/index.html`. |

The apply-phase evidence also records successful focused identity/platform-admin tests, PostgreSQL integration, backend lint, and the same fast/PostgreSQL BDD and 36-test Playwright lanes. Current verification supersedes the stale 27/27 invitation count and the stale 1,728 app-test count with the current 36/36 and 1,729 results. `openspec/config.yaml` defines no `coverage_threshold`, so no threshold comparison applies.

## Spec Compliance Matrix

| Requirement | Scenario | Runtime covering evidence | Result |
|---|---|---|---|
| Validate invitation before mutation | QA-06 expired | Fast and PostgreSQL Cucumber: `410 INVITATION_EXPIRED`, no registration mutation, invitation remains `ACTIVE`, one invitation, token/email redaction. Playwright: dedicated `410 INVITATION_EXPIRED` scenario passes in all three browser projects. | ✅ COMPLIANT |
| Validate invitation before mutation | QA-07 revoked | Fast and PostgreSQL Cucumber: `410 INVITATION_REVOKED`, no registration mutation, invitation remains `REVOKED`, one invitation, token/email redaction. Playwright: dedicated `410 INVITATION_REVOKED` scenario passes in all three browser projects. | ✅ COMPLIANT |
| Validate invitation before mutation | QA-08 normalized email mismatch | Fast and PostgreSQL Cucumber: `403 INVITATION_EMAIL_MISMATCH`, no registration mutation, invitation remains `ACTIVE`, one invitation, token/email redaction. Playwright: dedicated `403 INVITATION_EMAIL_MISMATCH` scenario passes in all three browser projects. | ✅ COMPLIANT |
| Validate invitation before mutation | QA-09 workspace override | Fast and PostgreSQL Cucumber: `400 INVITATION_WORKSPACE_OVERRIDE_NOT_ALLOWED` and no dispatch/mutation. | ✅ COMPLIANT |
| Invitation target determines workspace | Existing and new targets | Apply evidence plus current `just backend-check` PostgreSQL integration tests cover stored existing workspace, one new workspace, and membership resolution. | ✅ COMPLIANT |
| Existing identity is authenticated and non-duplicating | QA-10 matching identity | Fast and PostgreSQL Cucumber with `principal-1`, `jwt-user@example.com`, and `Bearer valid-token`: `200`, invitation-derived workspace, `ACTIVE` membership, `ACCEPTED` invitation, exactly one identity, credential, workspace, and membership. | ✅ COMPLIANT |
| Acceptance mutations are atomic | Commit, rollback, and concurrency | Current `just backend-check` includes PostgreSQL integration tests for rollback and concurrent one-winner acceptance; both completed successfully. | ✅ COMPLIANT |
| Verification follows existing policy | Valid invite-only registration | Current BDD suite includes the successful registration scenario with `PENDING` email policy, access token, workspace ID, and refresh cookie. | ✅ COMPLIANT |
| Evidence is redacted and aggregate | Rejected and accepted invitation evidence | Current BDD and Playwright tests assert no raw token/full email in responses or DOM; apply evidence covers redacted audit event and bounded telemetry tests. | ✅ COMPLIANT |
| One-time concurrent acceptance | Replay and concurrent contenders | Current backend check covers PostgreSQL one-winner behavior; current platform-admin BDD covers deterministic replay rejection. | ✅ COMPLIANT |
| Browser preserves invitation error classification | Expired, revoked, and mismatch | Current invitation Playwright run passes 36/36 across Chromium, Firefox, and Mobile Chrome, including all three explicit status/code journeys, safe copy, no refresh/session activity, stable URL, and DOM redaction. | ✅ COMPLIANT |
| Acceptance evidence is environment-qualified | Local evidence only | Fast Cucumber, PostgreSQL BDD, and Playwright results are local evidence and are not promoted to deployed/manual acceptance. | ✅ COMPLIANT |
| Follow-up preserves production behavior | QA-06..QA-10 test-only follow-up | Follow-up diff is limited to test features, glue, database assertions, and Playwright scenarios; no new production path is part of the follow-up. | ✅ COMPLIANT |

All twelve task items and all executable QA-06..QA-10 technical scenarios have passing local coverage. This report does not convert local evidence into product acceptance.

## Design Coherence

| Design decision | Result | Evidence |
|---|---|---|
| Reuse existing Cucumber/WebTestClient, PostgreSQL, and Playwright seams | ✅ FOLLOWED | The follow-up extends existing feature hooks, invitation state, database support, HAR/browser fixtures, and route overrides. |
| Reset state per scenario | ✅ FOLLOWED | Existing reset hooks remain in use; new counts and lifecycle fixtures are scoped to the Cucumber scenario state. |
| Keep raw tokens out of logs/assertion messages | ✅ FOLLOWED | Token is retained in test state only; assertions check redaction without printing it. Browser tests use route fixtures and DOM checks. |
| Revoke must carry an explicit code | ✅ FOLLOWED | Browser fixture returns `410` with `INVITATION_REVOKED`, avoiding the status-only expired fallback. |
| Preserve production behavior and configuration | ✅ FOLLOWED FOR FOLLOW-UP | No follow-up production source, API contract, classifier, copy, or runtime-configuration path was added. |
| Keep platformadmin-owned invitation failure mapping | ⚠️ INTENTIONAL DEVIATION | The implementation keeps identity advice free of platformadmin dependencies; current backend architecture and check lanes pass. |

## Strict-TDD and Runner Audit

`strict_tdd: true` is configured, but the supplemental verifier and deterministic quality runner are unavailable. This verification is therefore `fallback`; it does not claim deterministic strict-TDD enforcement.

| Check | Result | Evidence |
|---|---|---|
| Runtime RED/GREEN result for follow-up scenarios | ✅ PASS | Both Cucumber lanes and the 36-test Playwright lane pass after the follow-up. |
| Complete task-level RED transcript | ⚠️ UNAVAILABLE | `apply-progress.md` records successful implementation lanes but does not contain command output for every individual RED run. |
| Deterministic runner envelopes | ⚠️ UNAVAILABLE | No configured runner or versioned envelope exists in the repository. |

## Issues

### CRITICAL

None. No current test failure, missing covering test, incomplete task, or unresolved P0/P1 technical finding was found.

### WARNING

1. **P2 QA-16 — raw-token URL exposure remains open.** The unauthenticated handoff still uses `/register?invitationToken=...`. Server response, DOM, audit, logs, and metrics redaction pass, but browser history/referrer exposure is not addressed by this change.
2. **Manual/deployed acceptance remains blocked.** No deployed target, credentials, or controlled operator invitation fixtures were supplied. Local Cucumber, PostgreSQL, and mocked Playwright evidence must not be labeled deployed or manual acceptance. `qa-report.md` remains the acceptance authority and is `BLOCKED` for this capability.
3. **Acceptance QA limits remain visible.** Invitation accessibility and locale execution are `NOT TESTED` in `qa-report.md`; this technical report does not promote them to pass.
4. **Quality-runner fallback.** Strict-TDD configuration is active, but no deterministic verifier or complete task-level RED transcript is available.
5. **Fast BDD required a retry.** The first 300-second invocation was terminated by the execution harness; the 600-second retry passed in 4m 52s. No test failure was reported by the successful retry.
6. **Intentional advice ownership deviation.** Invitation failure mapping remains platformadmin-owned to preserve module boundaries; architecture and backend checks pass.

### SUGGESTION

1. Replace the raw-token registration URL handoff with a one-time server-side/browser handoff when the owning security/product decision is scheduled.

## Verification Judgment Table

| Finding | Judge A | Judge B | Severity | Status |
|---|---|---|---|---|
| QA-06 expired journey | ✅ Fast Cucumber and Playwright pass | ✅ PostgreSQL Cucumber and all browser projects pass | None | Confirmed |
| QA-07 revoked journey | ✅ Exact `410 INVITATION_REVOKED` and no mutation pass | ✅ Explicit browser code prevents expired fallback | None | Confirmed |
| QA-08 normalized mismatch journey | ✅ Exact `403 INVITATION_EMAIL_MISMATCH` and no mutation pass | ✅ Browser safe-copy and redaction checks pass | None | Confirmed |
| QA-09 workspace override | ✅ `400 INVITATION_WORKSPACE_OVERRIDE_NOT_ALLOWED` passes | ✅ No dispatch/mutation assertion passes | None | Confirmed |
| QA-10 matching existing identity | ✅ Fast Cucumber exact-one assertions pass | ✅ PostgreSQL Cucumber exact-one assertions pass | None | Confirmed |
| QA-06..QA-10 follow-up changed production behavior | ✅ Follow-up paths are test/fixture paths only | ✅ Apply evidence and source diff show no new production path in follow-up | None | Not found |
| Raw-token registration URL handoff | ✅ Source and E2E evidence | ✅ Browser history/referrer exposure remains unaddressed | P2 WARNING | Confirmed risk |
| Manual/deployed acceptance | ✅ No target or credentials supplied | ✅ QA report retains `BLOCKED`; local evidence is not promoted | Acceptance limitation | Blocked by environment |
| Deterministic strict-TDD runner | ✅ Configured strict TDD is true | ✅ Runner/verifier files are unavailable | Process warning | Fallback |
| Initial fast BDD timeout | ✅ Retry passed | ✅ Signal 15 came from the 300-second harness timeout, not a test assertion | Execution warning | Resolved by retry |
| All tasks complete | ✅ `tasks.md` has 12/12 checked | ✅ `apply-progress.md` records follow-up completion | None | Confirmed |

## Verdict

**PASS WITH WARNINGS**

Technical verification passes: all twelve tasks are complete, current fast and PostgreSQL BDD suites pass, QA-06 through QA-10 have runtime coverage, the invitation Playwright suite is 36/36, app tests are 1,729/1,729, and backend/app quality checks pass. The verdict carries warnings for QA-16, unavailable deterministic strict-TDD enforcement, the retried BDD command, and environment-qualified acceptance limits. Hand off to `sdd-qa`; deployed/manual acceptance remains `BLOCKED` until a target, credentials, and controlled invitation fixtures exist.
