## Verification Report

**Change**: `private-beta-launch-readiness`
**Unit**: `apply-unit-2-publishing-controls` (DALLAY-555/557)
**Mode**: OpenSpec
**Verified at**: `2026-08-23T12:21:47Z`
**Branch / base HEAD**: `feature/dallay-555-557-publishing-controls` / `c8215cd8`
**Execution mode**: `fallback` — no `sdd-quality-runner` was available; direct commands below preserve command identity, CWD, exit status, parser result, and artifact references.
**Strict TDD**: configured `true`; the runner and `strict-tdd-verify.md` module were unavailable, so strict-TDD enforcement is `UNAVAILABLE` and is not treated as a pass.

### Executive result

**PASS WITH WARNINGS.** Phase 2 publishing controls are technically conformant. Focused publishing/controller tests, both BDD lanes, the Spring Modulith boundary test, Spotless, backend check/build, and coverage all pass. The only remaining warnings are unavailable local Swarm rendering, absent managed-VPS/provider/user acceptance evidence, unfinished change-wide phases, and unavailable deterministic runner enforcement.

This report verifies technical conformance only. It does not claim managed-VPS, provider-side, operator, or user acceptance; `qa-report.md` owns the acceptance status. The orchestrator owns subsequent `tasks.md` and `state.yaml` delivery-state updates.

### Completeness

| Scope | Total | Complete | Incomplete | Result |
|---|---:|---:|---:|---|
| Current Phase 2 unit (`2.1`–`2.3`) | 3 | 3 | 0 | Complete |
| Change-wide top-level tasks | 15 | 8 | 7 | Phase 2 complete; later phases remain pending |

Pending change-wide tasks: `3.1`, `3.2`, `4.1`, `4.2`, `4.3`, `5.1`, and `5.2`. Phase 1 task `1.5` is now checked and is covered by the passing fast/PostgreSQL BDD evidence. The nested Phase 2.3 evidence bullets are checked. The orchestrator later advanced `state.yaml` to `current_phase: qa` with archive blocked by acceptance QA.

### Build, tests, and coverage evidence

| Command | CWD | Exit | Parser/result | Status | Redacted evidence / artifact |
|---|---|---:|---|---|---|
| `node scripts/with-db-password-gradle.mjs :server:smp:test --tests 'com.profiletailors.smp.publishing.*' --tests 'com.profiletailors.smp.platformadmin.infrastructure.http.PublishingStaleJobsControllerTest' --rerun-tasks --no-build-cache --no-daemon` | repository root | 0 | Gradle `BUILD SUCCESSFUL` | PASS | 140 selected Phase 2 publishing/controller tests; 0 skipped, failures, or errors. Results under `server/smp/build/test-results/test/`. |
| `SMP_DB_TEST_PASSWORD=test just backend-bdd-fast` | repository root | 0 | Gradle `BUILD SUCCESSFUL` | PASS | 203 scenarios; 0 skipped, failures, or errors. `publishing-stale-jobs.feature`: 8 scenarios. Results under `server/smp/build/test-results/bddFastTest/`. |
| `SMP_DB_TEST_PASSWORD=test just backend-bdd-postgres` | repository root | 0 | Gradle `BUILD SUCCESSFUL` | PASS | 203 scenarios; 0 skipped, failures, or errors. `publishing-stale-jobs.feature`: 8 scenarios. Results under `server/smp/build/test-results/bddPostgresTest/`. |
| `node scripts/with-db-password-gradle.mjs :server:smp:test --tests 'com.profiletailors.smp.ModularStructureTest' --rerun-tasks --no-daemon` | repository root | 0 | Gradle `BUILD SUCCESSFUL` | PASS | Spring Modulith boundary test passed; XML under `server/smp/build/test-results/test/`. |
| `just backend-test-fast` | repository root | 0 | Gradle `BUILD SUCCESSFUL` | PASS | Full fast backend test recipe passed; the recheck invocation was up-to-date after the fresh focused and boundary runs. |
| `SMP_DB_TEST_PASSWORD=test just backend-check` | repository root | 0 | Gradle `BUILD SUCCESSFUL` | PASS | Spotless, Detekt, tests, PostgreSQL integration, and Kover verification passed. |
| `SMP_DB_TEST_PASSWORD=test just backend-build` | repository root | 0 | Gradle `BUILD SUCCESSFUL` | PASS | `bootJar`, fast/PostgreSQL BDD, check, and build tasks passed. |
| `just backend-coverage` | repository root | 0 | Gradle `BUILD SUCCESSFUL` | PASS | `jacocoTestReport` completed; coverage task artifacts remain under `server/smp/build/`. |
| `./gradlew :server:smp:spotlessKotlinCheck --no-daemon` | repository root | 0 | Gradle `BUILD SUCCESSFUL` | PASS | Official Kotlin formatting check passed after the repository's `spotlessApply`. |
| `git diff --check` | repository root | 0 | no output | PASS | No whitespace errors in the final staged diff. |
| `just swarm-config` | repository root | 1 | renderer interpolation error | **UNAVAILABLE** | Required `DASHBOARD_IMAGE` is absent from the local Swarm environment; no rendered-stack evidence. Warning only. |

### Spec compliance matrix

| Requirement / scenario | Runtime covering test and implementation evidence | Result |
|---|---|---|
| Failure is visible and safe | `PublishingWorkerTest` canonical-failure/redaction coverage; `PublishingStaleJobsControllerTest`; stale BDD redaction scenario | ✅ COMPLIANT |
| Stale work is actionable with publication, workspace, age, and next action | `R2dbcPublishingRepositoriesUnitTest`, `PublishingHandlersTest`, and operator-list BDD scenario in both lanes; `ListStaleJobsHandler` maps `RELEASE_AND_RETRY` | ✅ COMPLIANT |
| Stale work is not silently published | Worker release-before-claim tests plus BDD assertions for queued status and `published_at IS NULL` | ✅ COMPLIANT |
| Worker safe-off prevents new delivery and preserves recoverability | `PublishingWorkerProperties.enabled=false`, `application.yaml:127-134`, Swarm override/runbook, `PublishingSchedulingConfigurationTest`, and worker tests | ✅ COMPLIANT |
| Unauthenticated request returns 401 | Controller test and `publishing-stale-jobs.feature` scenario | ✅ COMPLIANT |
| Unauthorized auditor request returns 403 / `PLATFORM_ACCESS_DENIED` | Controller test, role-permission regression test, and BDD scenario | ✅ COMPLIANT |
| Authorization is global rather than workspace-scoped | Controller is under `platformadmin.infrastructure.http`, requires `PUBLISHING_STALE_READ`, and does not resolve a workspace context; successful BDD/controller requests cover the global route | ✅ COMPLIANT |
| Invalid threshold and bounded limit return validation errors | Controller tests for malformed/non-positive threshold and lower/upper limits; BDD validation scenarios | ✅ COMPLIANT |
| Safe response schema contains no tokens, URLs, exceptions, provider payloads, or paths | `StaleJobItem`/`StaleJobsResponse` source shape, controller safe-response test, and BDD safe-shape scenario | ✅ COMPLIANT |
| BDD/schema alignment | Feature expects fields implemented by `StaleJobItem`, including `ageSeconds`, `attemptNumber`, `total`, and `suggestedAction=RELEASE_AND_RETRY`; the 8 scenarios pass in fast and PostgreSQL lanes | ✅ COMPLIANT |
| User-reported publish result remains `USER_REPORTED_OPERATIONAL` | No evidence-ledger/runtime acceptance path is added in Phase 2; proposal/design/spec/runbook retain the limitation | ➖ NOT APPLICABLE TO THIS UNIT; hand off to `sdd-qa` |

### Correctness and design coherence

| Finding | Judge A | Judge B | Severity | Status |
|---|---|---|---|---|
| Stale visibility selects only sufficiently expired `CLAIMED` rows and returns a bounded safe projection | ✅ `R2dbcPublicationJobRepository` and `ListStaleJobsHandler` source | ✅ Focused repository/handler tests plus both BDD lanes | REQUIRED | Confirmed |
| Stale recovery releases expired claims before `claimNextDue` and preserves retryability | ✅ `PublishingWorker.pollOnce` source and lease wiring | ✅ Worker and persistence tests, including release failure ordering | REQUIRED | Confirmed |
| Safe-off defaults and managed Swarm override are documented and reversible | ✅ `application.yaml`, `PublishingWorkerProperties`, `stack.yaml`, and runbook | ✅ Scheduling/worker tests; Swarm render is the only unavailable check | REQUIRED | Confirmed locally; VPS render warning remains |
| Authorization, positive ISO-8601 validation, bounded limits, and global admin route | ✅ Controller and `AdminProblemDetailsHandler` source | ✅ 9 controller tests plus 8 BDD scenarios in each lane | REQUIRED | Confirmed |
| Safe redaction and response/schema alignment | ✅ DTO shape exposes structural fields only | ✅ Controller and BDD safe-shape tests pass | REQUIRED | Confirmed |
| BDD and PostgreSQL alignment | ✅ `publishing-stale-jobs.feature` and step definitions match the response contract | ✅ 203/203 scenarios in each lane, including 8 stale-job scenarios | REQUIRED | Confirmed |
| Spring Modulith boundary conformance | ✅ Controller is now owned by `platformadmin.infrastructure.http`; publishing is consumed through the application query | ✅ Fresh `ModularStructureTest` `BUILD SUCCESSFUL` | **CRITICAL gate** | Confirmed; no violation remains |
| Kotlin formatting and repository link repair | ✅ Spotless-formatted source; runbook uses `../README.md` and `../architecture/adr/README.md` | ✅ Spotless check and `git diff --check` pass; targets resolve | REQUIRED | Confirmed |
| Evidence boundary | ✅ Proposal/design/spec/runbook keep code, VPS, provider, and `USER_REPORTED_OPERATIONAL` classifications distinct | ✅ This report makes no live acceptance claim | REQUIRED | Confirmed as a boundary; acceptance deferred |

#### Architecture assessment

The corrected design is coherent. Publishing domain ports/models remain framework-free; the application handler depends inward on the repository port and `Clock`; R2DBC, HTTP, and scheduling remain infrastructure concerns. The stale-jobs HTTP adapter now belongs to `platformadmin.infrastructure.http`, where its platform authorization dependencies are owned, and it consumes publishing through `ListStaleJobsQuery` on the Mediator. The fresh Spring Modulith test confirms the former dependency violation is removed.

#### Operational evidence assessment

The application default is safe-off (`SMP_PUBLISHING_WORKER_ENABLED:false`), while the Swarm file intentionally sets the private-beta operator override to `true` and documents the reversible `false` redeploy procedure. Local `just swarm-config` cannot render because the required `DASHBOARD_IMAGE` value is absent. No managed-VPS, provider-side, backup/restore, route, or real-user evidence was available or inferred.

### Issues

#### CRITICAL

None.

#### WARNING

1. `just swarm-config` is unavailable locally because the required `DASHBOARD_IMAGE` environment value is missing; static inspection passed, but rendered Swarm configuration was not proven.
2. Managed-VPS, provider-side, backup/restore, public/private route, operator, and user acceptance evidence is unavailable in this local verification. `sdd-qa` owns acceptance scenarios and `qa-report.md`; no acceptance claim is made here.
3. Change-wide tasks outside Phase 2 remain pending (`3.x`, `4.x`, and `5.x`) and must not be mistaken for completion of the entire private-beta change.
4. Strict-TDD enforcement is `UNAVAILABLE` because `strict_tdd: true` is configured but no quality runner or strict verification module is available. Runtime tests and repository quality gates nevertheless pass.

#### SUGGESTION

1. Run `just swarm-config` in an environment with the approved non-secret image variables, then attach the rendered configuration to the operator evidence record.

### Verdict

**PASS WITH WARNINGS**

Phase 2 `apply-unit-2-publishing-controls` passes technical verification: stale visibility/recovery, safe-off defaults and override, authorization/validation/global scope, redaction, BDD/schema alignment, boundary conformance, formatting, backend checks/build, and coverage are all supported by passing runtime evidence. The next phase is `sdd-qa` for capability-driven acceptance and managed-environment evidence; do not infer provider or user acceptance from this report.

**Status**: success
**Summary**: Phase 2 is technically verified with all repository-local gates passing; only environment/acceptance warnings remain.
**Artifacts**: `openspec/changes/private-beta-launch-readiness/verify-report.md`
**Next**: `sdd-qa` acceptance evidence remains the release gate
**Risks**: Unavailable local Swarm rendering, absent managed-VPS/provider/user acceptance evidence, pending change-wide phases, and unavailable strict-TDD runner enforcement.
**Skill Resolution**: `paths-injected` — repository standards and SDD verification protocol supplied by the orchestrator; quality-runner fallback used.
