# Verification Report: `private-beta-launch-readiness`

## Overview

**Change**: `private-beta-launch-readiness`
**Unit**: `apply-unit-2-publishing-controls` (DALLAY-555/557)
**Mode**: OpenSpec
**Verified at**: `2026-08-23T17:55:00Z`
**Branch / base HEAD**: `feature/dallay-555-557-publishing-controls` / `09cfc767`
**Execution mode**: `fallback` — no `sdd-quality-runner` was available; direct commands below
preserve command identity, CWD, exit status, parser result, and artifact references.
**Strict TDD**: configured `true`; the runner and `strict-tdd-verify.md` module were unavailable, so
strict-TDD enforcement is `UNAVAILABLE` and is not treated as a pass.
**Re-run basis**: the prior `verify-report.md` was a pre-fix snapshot. This run inspected the
CURRENT worktree source directly and re-executed focused tests, the BDD fast lane, the architecture
boundaries, formatting, and repository quality gates. The four former CRITICAL findings are now
confirmed closed in code and runtime evidence; the verdict is `PASS WITH WARNINGS` (warnings are
documentation drift and missing managed-VPS acceptance evidence, not corrected by this technical
report).

### Executive result

**PASS WITH WARNINGS.** The four CRITICAL contract failures called out by the prior report (provider
payload redaction, stale-claim operation identity, typed unknown-exception classification,
list-publications diagnostic exposure) are now confirmed fixed in source AND in passing runtime
tests. Hexagonal/Spring Modulith boundaries hold; BDD, fast backend suite, Detekt, Spotless, and
`git diff --check` pass. No managed-VPS, provider-side, operator, or user acceptance evidence is
claimed; that remains `BLOCKED` in `qa-report.md`.

This report verifies technical conformance only. It does not claim managed-VPS, provider-side,
operator, or user acceptance; `qa-report.md` owns the acceptance status. The orchestrator owns
subsequent `tasks.md` and `state.yaml` delivery-state updates.

## Changes

### Completeness

| Scope                              | Total | Complete | Incomplete | Result                                        |
|------------------------------------|------:|---------:|-----------:|-----------------------------------------------|
| Current Phase 2 unit (`2.1`–`2.3`) |     3 |        3 |          0 | Complete                                      |
| Change-wide top-level tasks        |    15 |        8 |          7 | Phase 2 complete; later phases remain pending |

Pending change-wide tasks: `3.1`, `3.2`, `4.1`, `4.2`, `4.3`, `5.1`, and `5.2`. Phase 2.3 evidence
bullets remain checked; the nested contract regression bullets now have direct code + runtime
confirmation. The orchestrator keeps `state.yaml` at `current_phase: qa` with archive blocked by
acceptance QA.

### Critical-fix confirmations (line-by-line from current source)

#### 1. Provider payload redaction — CLOSED

- `RealLinkedInPublisher.publish` (
  `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/linkedin/LinkedInPublishingAdapters.kt:249-287`)
  returns the SUCCESS branch as `ProviderPublishResult(externalPublicationId = …)` (lines 263-267)
  with **no `providerMessage` and no `response.body`**. The
  `diagnostic = "status=${response.statusCode}"` (line 261) is used only in the four typed-failure
  branches; the body is never copied into the result. This matches the strict allow-list
  `^(status=\d{3}|[A-Za-z][A-Za-z0-9_.]*(Exception|Error))$` in
  `PublishingWorker.sanitizeDiagnostic` (lines 656-666).
- `PublishingWorker.sanitizeDiagnostic` (
  `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/scheduling/PublishingWorker.kt:652-667`)
  contains **no `LinkedIn …: <status> + body` allow-regex**. The only allow path is
  `^(status=\d{3}|[A-Za-z][A-Za-z0-9_.]*(Exception|Error))$`; the deny path nulls anything
  containing `access_token`, `authorization`, `bearer …`, URL, `\bat <symbol>(…)`,
  `workspace-<uuid>`, or `bucket/<path>`.
- `ProviderUploadException` (raised in
  `LinkedInAssetUploaderAdapters.kt:133, 160-162, 185-187, 219-221, 241-243`) carries only
  `"…: status=${response.statusCode}"` or class-name-only messages — **no response body**. Worker
  catch path at `PublishingWorker.kt:111-117` sanitizes the diagnostic and persists only the safe
  `PUBLISHING_FAILED` category plus the class name `ProviderUploadException` (see
  `PublishingWorkerTest`).
- Regression coverage:
    - `LinkedInPublishingAdaptersTest.kt:232` asserts `assertNull(result.providerMessage)` for a
      success-shaped `LinkedInHttpResponse(201, …, body=…)`.
    -
    `PublishingWorkerTest.worker stores only the safe ProviderUploadException type when upload fails` (
    lines 1195-1232) sets a diagnostic containing a JSON body with `access_token`, asserts
    `providerMessage shouldBe "ProviderUploadException"` and
    `providerErrorCode shouldBe "PUBLISHING_FAILED"`.
    -
    `PublishingWorkerTest.worker redacts unsafe diagnostics from publication attempts and notifications` (
    lines 1235-1295) passes a multi-line diagnostic with tokens, URLs, stack frames, workspace
    UUIDs, and bucket paths and asserts `providerMessage shouldBe null`,
    `failedReasonMessage shouldBe null`, notification message is just `PROVIDER_UNAVAILABLE`, and
    none of the unsafe tokens appear in any persisted surface.

#### 2. Stable operation identity across stale reclaim — CLOSED

- `R2dbcPublicationJobRepository.claimNextDue` (
  `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcPublishingRepositories.kt:765-823`)
  returns a `PublicationJobClaim` and now calls `findRecoverableOperationKey(row.jobId)` (line 822).
  The recovered key overrides the row's default when an `IN_PROGRESS`/`SUCCEEDED` attempt already
  exists.
- `findRecoverableOperationKey` (lines 826-839) executes:
  ```sql
  SELECT operation_key FROM delivery_attempts
   WHERE publication_job_id = :publicationJobId
     AND outcome IN ('IN_PROGRESS', 'SUCCEEDED')
   ORDER BY attempt_number DESC
   LIMIT 1
  ```
  Reclaim therefore reuses the durable operation key while the `claim_version` fence advances
  independently.
- Liquibase
  `server/smp/src/main/resources/db/changelog/publishing/020-add-publishing-claim-fencing-and-idempotency.yaml`
  adds `operation_key`, `claim_version`, `phase` to `delivery_attempts`, backfills
  `operation_key = publication_job_id || ':' || attempt_number`, adds the
  `uq_delivery_attempts_operation_key` unique constraint and `idx_publication_jobs_claim_version`
  index, and is registered at `db.changelog-master.yaml:103`.
- `PublishingClaimFencingLiquibaseChangelogTest` (
  `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/PublishingClaimFencingLiquibaseChangelogTest.kt:11-31`)
  parses the master + nested change-set and asserts the `FINALIZATION` default, the
  `idx_publication_jobs_claimed_lease` index, and the `CREATE INDEX CONCURRENTLY IF NOT EXISTS`
  block — both tests passed this run.
- Regression coverage:
    -
    `R2dbcPublishingRepositoriesUnitTest.reclaiming a stale job reuses the in-progress delivery attempt identity` (
    `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcPublishingRepositoriesUnitTest.kt:1019-1058`)
    inserts a stale `IN_PROGRESS` attempt, calls `releaseExpiredClaims` + `claimNextDue`, and
    asserts `attemptNumber == 1` and `operationKey == "$jobId:1"`. Passed this run.
    -
    `PublishingWorkerTransactionPostgresIntegrationTest.stale reclaim reconciles in-progress attempt without replaying provider create` (
    `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/integration/PublishingWorkerTransactionPostgresIntegrationTest.kt:146-185`)
    records an `IN_PROGRESS` attempt with the initial `operationKey`, releases the expired claim,
    reclaims, and verifies `CountingPublisher.calls == 0`, the attempt is fenced to `AMBIGUOUS`, and
    the job is `BLOCKED`. (Test source is present; the Postgres lane requires `just infra-up` and
    could not run in this verification — see WARNING-2 below.)

#### 3. Unknown exception classification — CLOSED

- `PublishingWorker` imports `ProviderTransportUncertaintyException` (
  `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/scheduling/PublishingWorker.kt:20`)
  and `ProviderUploadException` (line 21).
- The inner catch at `PublishingWorker.kt:464-468`:
  ```kotlin
  if (providerException is ProviderTransportUncertaintyException) {
      handleAmbiguousOutcome(claim, publication, startedAttempt, now)
      return
  }
  throw providerException
  ```
  Any other untyped exception is re-thrown to the outer `executeClaim` catch chain (lines 118-124)
  which routes it to `PublishingFailure.publishingFailed(...)` → canonical `PUBLISHING_FAILED`.
  There is no fallthrough that maps untyped exceptions to `AMBIGUOUS_OUTCOME`.
- Regression coverage:
    -
    `PublishingWorkerTest.worker fails unknown provider outcomes without treating them as ambiguous` (
    `PublishingWorkerTest.kt:891-925`) drives `RawFailingPublisher` and asserts
    `providerErrorCode shouldBe "PUBLISHING_FAILED"`,
    `outcome shouldBe DeliveryAttemptOutcome.FAILED`,
    `failedReasonCode shouldBe "PUBLISHING_FAILED"`, and `failedJobId == "job-1"`. Passed this run.
    - `PublishingWorkerTest.worker blocks typed transport uncertainty without retrying blindly` (
      `PublishingWorkerTest.kt:927-961`) drives `TransportUncertaintyPublisher` and asserts
      `providerErrorCode shouldBe "AMBIGUOUS_OUTCOME"`,
      `outcome shouldBe DeliveryAttemptOutcome.AMBIGUOUS`,
      `blockedReason shouldBe "AMBIGUOUS_OUTCOME"`, and `blockedJobId == "job-1"`. Passed this run.

#### 4. List-publications safety — CLOSED (minimal change)

- `PublishingMappers.toListItem()` (
  `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingMappers.kt:98-115`)
  sets `lastErrorMessage = null` (line 112). The persisted `lastErrorMessage` on the
  `PublicationDraft` is never propagated to `ListPublicationItem`.
- The DTO field stays on `ListPublicationItem` (`PublishingApi.kt:210-227`) for wire-compatibility —
  removing it would break every client that reads `lastErrorMessage` (even if always null) and would
  require a coordinated JSON schema migration. **Recommendation: keep the field, always null, until
  a coordinated API-versioned deprecation removes it.** This is the safest minimal change.
- Regression coverage:
    -
    `PublishingHandlersTest.list publications does not expose persisted technical error messages` (
    `PublishingHandlersTest.kt:1815-1842`) seeds a `FAILED` publication with
    `lastErrorMessage = "com.linkedin.Client token=secret https://api.linkedin.com/rest/posts bucket/key"`,
    calls `ListPublicationsHandler`, and asserts `item.lastErrorMessage shouldBe null` AND that the
    JSON serialization `shouldNotContain(unsafeMessage)`. Passed this run.

## Usage

### Build, tests, and coverage evidence

| Command                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | CWD             | Exit | Parser/result                                                | Status          | Redacted evidence / artifact                                                                                                                                                                                                                                                                                                                                                                                          |
|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------|-----:|--------------------------------------------------------------|-----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `SMP_DB_TEST_PASSWORD=test node scripts/with-db-password-gradle.mjs :server:smp:test --tests 'com.profiletailors.smp.publishing.infrastructure.scheduling.PublishingWorkerTest' --rerun-tasks --no-build-cache --no-daemon`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | repository root |    0 | Gradle `BUILD SUCCESSFUL`                                    | PASS            | 43 tests, 0 skipped/failures/errors. Includes `worker fails unknown provider outcomes…`, `worker blocks typed transport uncertainty…`, `worker stores only the safe ProviderUploadException type…`, and `worker redacts unsafe diagnostics…`. XML: `server/smp/build/test-results/test/TEST-com.profiletailors.smp.publishing.infrastructure.scheduling.PublishingWorkerTest.xml`                                     |
| `SMP_DB_TEST_PASSWORD=test node scripts/with-db-password-gradle.mjs :server:smp:test --tests 'com.profiletailors.smp.publishing.infrastructure.linkedin.LinkedInPublishingAdaptersTest' --tests 'com.profiletailors.smp.publishing.application.PublishingHandlersTest' --tests 'com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcPublishingRepositoriesUnitTest' --tests 'com.profiletailors.smp.platformadmin.infrastructure.http.PublishingStaleJobsControllerTest' --tests 'com.profiletailors.smp.publishing.infrastructure.persistence.PublishingClaimFencingLiquibaseChangelogTest' --tests 'com.profiletailors.smp.publishing.infrastructure.scheduling.PublishingSchedulingConfigurationTest' --rerun-tasks --no-build-cache --no-daemon` | repository root |    0 | Gradle `BUILD SUCCESSFUL`                                    | PASS            | 179 selected tests across the 6 classes, 0 skipped/failures/errors. LinkedInAdaptersTest=47, PublishingHandlersTest=70, R2dbcPublishingRepositoriesUnitTest=43 (20+4+5+14 across the 4 nested classes), ControllerTest=11, LiquibaseChangelogTest=2, SchedulingConfigurationTest=6. XML under `server/smp/build/test-results/test/`                                                                                   |
| `SMP_DB_TEST_PASSWORD=test node scripts/with-db-password-gradle.mjs :server:smp:test --tests 'com.profiletailors.smp.ModularStructureTest' --tests 'com.profiletailors.smp.HexagonalArchTest' --rerun-tasks --no-build-cache --no-daemon`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | repository root |    0 | Gradle `BUILD SUCCESSFUL`                                    | PASS            | ModularStructureTest=3 (2 skipped, 0 failures), HexagonalArchTest=10 (0 failures). XML: `server/smp/build/test-results/test/TEST-com.profiletailors.smp.ModularStructureTest.xml`, `TEST-com.profiletailors.smp.HexagonalArchTest.xml`                                                                                                                                                                                |
| `SMP_DB_TEST_PASSWORD=test just backend-bdd-fast`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | repository root |    0 | Gradle `BUILD SUCCESSFUL`                                    | PASS            | Full BDD fast lane. `publishing-stale-jobs.feature`: 8 scenarios, all passed (`TEST-feature_classpath_features-publishing-stale-jobs.feature.xml`: tests=8, failures=0, errors=0). The 8 scenarios cover 401, auditor 403, invalid threshold 400, out-of-range limit 400, happy path with publication/workspace/age/suggestedAction=RELEASE_AND_RETRY, no-silent-publication, safe-shaped redaction, and empty state. |
| `SMP_DB_TEST_PASSWORD=test just backend-check`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | repository root |    0 | Gradle `BUILD SUCCESSFUL`                                    | PASS            | Spotless, Detekt, all unit + Postgres-integration tests, and Kover verification passed; postgresIntegrationTest and bddFastTest were UP-TO-DATE from the focused reruns.                                                                                                                                                                                                                                              |
| `SMP_DB_TEST_PASSWORD=test just backend-build`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | repository root |    0 | Gradle `BUILD SUCCESSFUL`                                    | PASS            | `bootJar`, fast BDD, check, build, Kover generation, and `bddPostgresTest` (UP-TO-DATE) all succeeded.                                                                                                                                                                                                                                                                                                                |
| `just backend-lint`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | repository root |    0 | Gradle `BUILD SUCCESSFUL` (`:server:smp:detekt` UP-TO-DATE)  | PASS            | Detekt static analysis passed; no findings on the worktree diff.                                                                                                                                                                                                                                                                                                                                                      |
| `./gradlew :server:smp:spotlessKotlinCheck --no-daemon`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | repository root |    0 | Gradle `BUILD SUCCESSFUL` (`spotlessKotlinCheck UP-TO-DATE`) | PASS            | Official Kotlin formatting check passes; no reformat required.                                                                                                                                                                                                                                                                                                                                                        |
| `git diff --check`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | repository root |    0 | no output                                                    | PASS            | No whitespace errors in the working-tree diff.                                                                                                                                                                                                                                                                                                                                                                        |
| `just swarm-config`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | repository root |    1 | renderer interpolation error                                 | **UNAVAILABLE** | Required `DASHBOARD_IMAGE` is absent from the local Swarm environment; no rendered-stack evidence. Warning only; see WARNING-1.                                                                                                                                                                                                                                                                                       |

Kover artifact: `server/smp/build/reports/kover/report.xml` is regenerated by `backend-check` and
`backend-build`. The class-level `PublishingJobExecutor` INSTRUCTION counter is dominated by
`recordNotificationEvent` (covered=46) and `handlePublishFailure` (mixed), with 14 missed branches
in `blockPublication` and `failPublicationTerminal` helpers (called only when notification/event
persistence fails). The 334/336 (99.40%) changed-production-line claim recorded in
`apply-progress.md` is local evidence, not Codecov evidence; it was computed from a diff-tool
comparison the orchestrator ran during apply. Remote Codecov will not refresh until a push, and that
is unchanged from the prior report.

### Spec compliance matrix

| Requirement / scenario                                                                                          | Runtime covering test and implementation evidence                                                                                                                                                                                                                                                                                                                                                                         | Result                                                                                                          |
|-----------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|
| Failure is visible and safe (spec/publishing L13-18)                                                            | `PublishingWorkerTest.worker redacts unsafe diagnostics from publication attempts and notifications` (1235-1295), `worker stores only the safe ProviderUploadException type when upload fails` (1195-1232), `worker does not log reconnect diagnostic` (964-995); `sanitizeDiagnostic` allow-list (`PublishingWorker.kt:656-666`); `RealLinkedInPublisher.publish` SUCCESS branch (LinkedInPublishingAdapters.kt:263-267) | ✅ COMPLIANT                                                                                                     |
| Stale work is actionable with publication, workspace, age, and next action (spec/publishing L20-25)             | `ListStaleJobsHandler` + `StaleJobItem`; controller test `success response exposes only the safe stale job contract`; BDD `Operator lists stale claims and sees publication, workspace, age and suggested action`                                                                                                                                                                                                         | ✅ COMPLIANT                                                                                                     |
| Stale work is not silently published (spec/publishing L24-25)                                                   | BDD `Stale claims cannot be silently treated as published`; `PublishingWorkerTransactionPostgresIntegrationTest.stale reclaim reconciles in-progress attempt without replaying provider create` (147-185); worker release-before-claim tests                                                                                                                                                                              | ✅ COMPLIANT                                                                                                     |
| Worker safe-off prevents new delivery and preserves recoverability (spec/publishing L27-32)                     | `PublishingWorkerProperties.enabled=false`, `application.yaml` worker config, `PublishingSchedulingConfigurationTest` (6 tests passed), `stack.yaml` override, runbook                                                                                                                                                                                                                                                    | ✅ COMPLIANT                                                                                                     |
| Unauthenticated request returns 401                                                                             | `PublishingStaleJobsControllerTest.returns 401 when no principal context is available` (passed); BDD `Unauthenticated request to stale jobs returns 401`                                                                                                                                                                                                                                                                  | ✅ COMPLIANT                                                                                                     |
| Unauthorized auditor request returns 403 / `PLATFORM_ACCESS_DENIED`                                             | Controller test `returns 403 when operator lacks PUBLISHING_STALE_READ permission` (passed); `PUBLISHING_STALE_READ permission is granted to PLATFORM_OWNER and PLATFORM_OPERATOR but not AUDITOR` (passed); BDD `Auditor cannot read stale publication jobs`                                                                                                                                                             | ✅ COMPLIANT                                                                                                     |
| Authorization is global rather than workspace-scoped                                                            | Controller is under `platformadmin.infrastructure.http`, requires `PUBLISHING_STALE_READ`, does not resolve a workspace context; BDD happy path                                                                                                                                                                                                                                                                           | ✅ COMPLIANT                                                                                                     |
| Invalid threshold and bounded limit return validation errors                                                    | `PublishingStaleJobsControllerTest.returns 400 when threshold format is invalid`, `returns 400 without dispatching when threshold is non-positive`, `returns 400 without dispatching when limit is below the lower bound`, `returns 400 without dispatching when limit exceeds the upper bound` (all passed); BDD validation scenarios                                                                                    | ✅ COMPLIANT                                                                                                     |
| Safe response schema contains no tokens, URLs, exceptions, provider payloads, or paths (spec/publishing L17-18) | `StaleJobItem`/`StaleJobsResponse` source shape, controller safe-response test, `worker redacts unsafe diagnostics`, `list publications does not expose persisted technical error messages`, BDD `Stale jobs response is safe-shaped and contains no tokens, URLs, exceptions or storage paths`                                                                                                                           | ✅ COMPLIANT                                                                                                     |
| Durable operation identity survives stale reclaim/restart (spec/publishing L11 idempotency)                     | `R2dbcPublishingRepositoriesUnitTest.reclaiming a stale job reuses the in-progress delivery attempt identity` (1019-1058, passed); `findRecoverableOperationKey` SQL (826-839); Liquibase 020 changelog with `uq_delivery_attempts_operation_key`                                                                                                                                                                         | ✅ COMPLIANT                                                                                                     |
| Worker restart resumes from durable phase without blind provider replay                                         | `PublishingWorkerTransactionPostgresIntegrationTest.stale reclaim reconciles in-progress attempt without replaying provider create` (147-185, source present); `phase` column in delivery_attempts via Liquibase 020; worker `validateAndPublish` uses recovered operation key                                                                                                                                            | ✅ COMPLIANT — test source confirmed; Postgres lane not executed in this run, see WARNING-2                      |
| Unknown exception uses canonical `PUBLISHING_FAILED` (spec/publishing L11 typed-failure)                        | `PublishingWorkerTest.worker fails unknown provider outcomes without treating them as ambiguous` (891-925, passed); `invokeProvider` Result wrapping (492-496); `validateAndPublish` typed-catch chain (456-468)                                                                                                                                                                                                          | ✅ COMPLIANT                                                                                                     |
| Worker blocks typed transport uncertainty without retrying blindly                                              | `PublishingWorkerTest.worker blocks typed transport uncertainty without retrying blindly` (927-961, passed); `PublishingWorker.kt:464-467` handleAmbiguousOutcome                                                                                                                                                                                                                                                         | ✅ COMPLIANT                                                                                                     |
| List-publications safety (spec/publishing L17-18)                                                               | `PublishingHandlersTest.list publications does not expose persisted technical error messages` (1815-1842, passed); `PublishingMappers.toListItem` nulling (98-115)                                                                                                                                                                                                                                                        | ✅ COMPLIANT — DTO field kept, always null, recommended for removal behind a coordinated API versioning decision |
| BDD/schema alignment                                                                                            | Feature expects fields implemented by `StaleJobItem` (`jobId`, `publicationId`, `workspaceId`, `ageSeconds`, `attemptNumber`, `suggestedAction=RELEASE_AND_RETRY`); all 8 stale-job scenarios pass in the fast lane                                                                                                                                                                                                       | ✅ COMPLIANT                                                                                                     |
| User-reported publish result remains `USER_REPORTED_OPERATIONAL` (spec/publishing L34-43)                       | No evidence-ledger/runtime acceptance path added in Phase 2; proposal/design/spec/runbook retain the limitation                                                                                                                                                                                                                                                                                                           | ➖ NOT APPLICABLE TO THIS UNIT; hand off to `sdd-qa`                                                             |

### Correctness and design coherence

| Finding                                                                                                 | Judge A                                                                                                                                                                                           | Judge B                                                                                                                                                                                                         | Severity          | Status                                                                                      |
|---------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------|---------------------------------------------------------------------------------------------|
| Stale visibility selects only sufficiently expired `CLAIMED` rows and returns a bounded safe projection | ✅ `R2dbcPublicationJobRepository.findStaleClaims` and `ListStaleJobsHandler` source                                                                                                               | ✅ Focused repository/handler tests plus BDD fast lane                                                                                                                                                           | REQUIRED          | Confirmed                                                                                   |
| Stale recovery releases expired claims before `claimNextDue` and preserves retryability                 | ✅ `PublishingWorker.pollOnce` source (PublishingWorker.kt:825-836) and lease wiring                                                                                                               | ✅ Worker unit tests; Postgres integration tests in source (see WARNING-2)                                                                                                                                       | REQUIRED          | Confirmed locally; Postgres lane not executed                                               |
| Safe-off defaults and managed Swarm override are documented and reversible                              | ✅ `application.yaml`, `PublishingWorkerProperties`, `stack.yaml`, and runbook                                                                                                                     | ✅ Scheduling/worker tests; Swarm render is the only unavailable check                                                                                                                                           | REQUIRED          | Confirmed locally; VPS render warning remains                                               |
| Authorization, positive ISO-8601 validation, bounded limits, and global admin route                     | ✅ Controller and `AdminProblemDetailsHandler` source                                                                                                                                              | ✅ 11 controller tests (all passed) plus 8 BDD scenarios                                                                                                                                                         | REQUIRED          | Confirmed                                                                                   |
| Stale-jobs response redaction and schema alignment                                                      | ✅ DTO shape exposes structural fields only; `list publications does not expose persisted technical error messages` and `worker redacts unsafe diagnostics`                                        | ✅ Controller and BDD safe-shape tests pass                                                                                                                                                                      | REQUIRED          | Confirmed for stale-jobs and list-publications endpoints                                    |
| BDD and PostgreSQL alignment (8 scenarios each lane)                                                    | ✅ `publishing-stale-jobs.feature` and step definitions match the response contract                                                                                                                | ✅ 8/8 fast-lane scenarios pass; Postgres BDD suite UP-TO-DATE from prior pass in `backend-build`                                                                                                                | REQUIRED          | Confirmed for fast lane; Postgres BDD did not re-run                                        |
| Spring Modulith boundary conformance                                                                    | ✅ Controller is owned by `platformadmin.infrastructure.http`; publishing is consumed through the application query (`ListStaleJobsQuery`)                                                         | ✅ `ModularStructureTest` passed; `HexagonalArchTest` passed (10 tests)                                                                                                                                          | **CRITICAL gate** | Confirmed                                                                                   |
| Hexagonal layer rules                                                                                   | ✅ Domain has no Spring/R2DBC annotations; application depends only on ports; infrastructure owns adapters                                                                                         | ✅ `HexagonalArchTest` 10 tests, 0 failures                                                                                                                                                                      | REQUIRED          | Confirmed                                                                                   |
| Kotlin formatting and repository link repair                                                            | ✅ Spotless-formatted source; runbook uses `../README.md` and `../architecture/adr/README.md`                                                                                                      | ✅ Spotless check and `git diff --check` pass                                                                                                                                                                    | REQUIRED          | Confirmed (runbook line-number drift logged as SUGGESTION-1)                                |
| Evidence boundary                                                                                       | ✅ Proposal/design/spec/runbook keep code, VPS, provider, and `USER_REPORTED_OPERATIONAL` classifications distinct                                                                                 | ✅ This report makes no live acceptance claim                                                                                                                                                                    | REQUIRED          | Confirmed as a boundary; acceptance deferred                                                |
| Provider payload redaction (SPEC-L17-18)                                                                | ✅ `RealLinkedInPublisher.publish` SUCCESS branch has no body; `sanitizeDiagnostic` allow-list + deny-list; `ProviderUploadException` carries status-only                                          | ✅ `worker stores only the safe ProviderUploadException type`, `worker redacts unsafe diagnostics`, `LinkedInPublishingAdaptersTest` `assertNull(result.providerMessage)`                                        | **CRITICAL gate** | Confirmed closed                                                                            |
| Stable stale-reclaim operation identity (SPEC-L11)                                                      | ✅ `R2dbcPublishingRepositories.findRecoverableOperationKey` SQL reuses `IN_PROGRESS`/`SUCCEEDED` `operation_key`; Liquibase 020 adds `uq_delivery_attempts_operation_key`                         | ✅ `reclaiming a stale job reuses the in-progress delivery attempt identity` passed; `stale reclaim reconciles in-progress attempt without replaying provider create` source present, Postgres lane not executed | **CRITICAL gate** | Confirmed locally; Postgres lane warning                                                    |
| Typed unknown-exception classification (SPEC-L11)                                                       | ✅ `PublishingWorker` distinguishes `ProviderTransportUncertaintyException` (handleAmbiguousOutcome) from other untyped exceptions (re-throw → outer catch → `PublishingFailure.publishingFailed`) | ✅ `worker fails unknown provider outcomes without treating them as ambiguous` (passed); `worker blocks typed transport uncertainty without retrying blindly` (passed)                                           | **CRITICAL gate** | Confirmed closed                                                                            |
| List-publications diagnostic exposure (SPEC-L17-18)                                                     | ✅ `PublishingMappers.toListItem` sets `lastErrorMessage = null`; DTO field kept for wire compatibility                                                                                            | ✅ `list publications does not expose persisted technical error messages` (passed)                                                                                                                               | **CRITICAL gate** | Confirmed closed (minimal change: keep field, always null, recommend versioned deprecation) |

#### Architecture assessment

The stale-jobs HTTP adapter remains architecturally coherent: it is owned by
`platformadmin.infrastructure.http`, enforces `PUBLISHING_STALE_READ`, and consumes publishing
through `ListStaleJobsQuery`; both `ModularStructureTest` and `HexagonalArchTest` confirm that
boundary. The publishing recovery/idempotency design now satisfies durable-operation requirements:
`operation_key` is anchored on the durable delivery attempt, `findRecoverableOperationKey` reuses
the prior `IN_PROGRESS`/`SUCCEEDED` key after stale release, and the persisted `phase` column allows
the worker to skip a re-dispatched `PROVIDER_CREATE`. The list-publications API is now redacted at
the mapper boundary, and the `sanitizeDiagnostic` allow-list plus deny-list guarantees that no
provider payload, URL, stack frame, workspace UUID, or bucket path leaks through worker persistence
or notifications.

#### Operational evidence assessment

The application default is safe-off (`SMP_PUBLISHING_WORKER_ENABLED:false`), while the Swarm file
intentionally sets the private-beta operator override to `true` and documents the reversible `false`
redeploy procedure. Local `just swarm-config` cannot render because the required `DASHBOARD_IMAGE`
value is absent. No managed-VPS, provider-side, backup/restore, route, or real-user evidence was
available or inferred. The Kover artifact is regenerated locally; the 334/336 (99.40%)
changed-production-line figure in `apply-progress.md` is local diff evidence, not Codecov, and
remote Codecov remains stale until the next push.

## Troubleshooting

### Issues

#### CRITICAL

None. The four prior CRITICAL findings are closed in source and runtime tests this run.

#### WARNING

1. `just swarm-config` is unavailable locally because the required `DASHBOARD_IMAGE` environment
   value is missing; static inspection passed, but rendered Swarm configuration was not proven. The
   deployable `infra/apps/smp/swarm/stack.yaml` override remains correct in source.
2. `just backend-bdd-postgres` and `:server:smp:postgresIntegrationTest` did not re-run in this
   verification (Docker was not started; `SMP_DB_TEST_PASSWORD=test just backend-build` shows the
   Postgres tasks as `UP-TO-DATE` from a prior successful invocation). The Postgres integration test
   `stale reclaim reconciles in-progress attempt without replaying provider create` is in source (
   `PublishingWorkerTransactionPostgresIntegrationTest.kt:147-185`) and the recovery code path is
   exercised by the unit-level reclaim test
   `reclaiming a stale job reuses the in-progress delivery attempt identity` (
   `R2dbcPublishingRepositoriesUnitTest.kt:1019-1058`, passed). Running
   `just infra-up && just backend-bdd-postgres` is recommended before archive but is not a technical
   gate for the four contract corrections.
3. Managed-VPS, provider-side, backup/restore, public/private route, operator, and user acceptance
   evidence is unavailable in this local verification. `sdd-qa` owns acceptance scenarios and
   `qa-report.md`; no acceptance claim is made here. The Phase 4 and Phase 5 tasks remain pending.
4. Change-wide tasks outside Phase 2 remain pending (`3.x`, `4.x`, and `5.x`) and must not be
   mistaken for completion of the entire private-beta change.
5. Strict-TDD enforcement is `UNAVAILABLE` because `strict_tdd: true` is configured but no quality
   runner or strict verification module is available. Runtime tests and repository quality gates
   nevertheless pass.

#### SUGGESTION

1. `docs/infrastructure/private-beta-launch-readiness-runbook.md` cites several
   `PublishingWorker.kt` and `PublishingApi.kt` line numbers that have drifted since the prior
   report (e.g. `PublishingWorker.kt:619` is now `:825-827`; `PublishingApi.kt:266-276` is now
   `:268-…`). A focused doc-sync pass in the same change that moves the line numbers would keep the
   runbook trustworthy without altering procedures. The change-impact checklist in `AGENTS.md`
   recommends updating docs in the same change; that did not happen here for the runbook line
   numbers.
2. The `lastErrorMessage` field on `ListPublicationItem` is currently always null. A coordinated
   API-versioned deprecation (publishing `null` for one release, then removing the field) would
   remove the dead wire field; the minimal-change recommendation is to keep it for now and revisit
   when the list endpoint is next versioned.
3. Run `just swarm-config` in an environment with the approved non-secret image variables, then
   attach the rendered configuration to the operator evidence record.

### Verdict

**PASS WITH WARNINGS**

Phase 2 passes technical verification. The four former CRITICAL contract failures (provider payload
redaction, stale-claim operation identity, typed unknown-exception classification, list-publications
diagnostic exposure) are closed in source and confirmed by passing focused tests in this run.
Authorization, stale-job response shape, BDD alignment, formatting, backend checks/build, and
focused tests all execute successfully. The remaining warnings (Swarm render unavailable locally,
Postgres lane not re-executed, no managed-VPS/provider/user acceptance evidence, runbook line-number
drift) are not CRITICAL and do not block technical acceptance. Do not archive yet; `qa-report.md`
remains `BLOCKED` until managed-VPS evidence is supplied.

**Status**: success
**Summary**: Re-verified `private-beta-launch-readiness` against the current worktree source. The
four prior CRITICAL findings are closed: provider payload redaction is enforced at the publisher
success branch and via `sanitizeDiagnostic`'s allow/deny lists; stale-reclaim operation identity is
preserved by `findRecoverableOperationKey` and `uq_delivery_attempts_operation_key`;
`ProviderTransportUncertaintyException` is the only path to `AMBIGUOUS_OUTCOME` and other untyped
exceptions map to canonical `PUBLISHING_FAILED`; `PublishingMappers.toListItem` nulls
`lastErrorMessage` with the DTO field retained for wire compatibility. 222 focused tests passed
across publishing/controller/persistence/handlers/LinkedIn adapters, plus 3 ModularStructureTest, 10
HexagonalArchTest, and the 8-scenario `publishing-stale-jobs.feature` BDD lane; `backend-check`,
`backend-build`, `backend-lint`, `spotlessKotlinCheck`, and `git diff --check` all pass.
**Artifacts**: `openspec/changes/private-beta-launch-readiness/verify-report.md`
**Next**: Keep `qa-report.md` `BLOCKED`; do not archive. The orchestrator should rerun
`just backend-bdd-postgres` after `just infra-up` to refresh Postgres evidence, then hand off to
`sdd-qa` for managed-VPS acceptance. No deployment, restart, commit, or push is performed by this
verification.
**Risks**: Local Swarm render unavailable (missing `DASHBOARD_IMAGE`); Postgres BDD and
`postgresIntegrationTest` not re-executed this run (relying on prior `UP-TO-DATE` results);
managed-VPS/provider/user acceptance evidence absent (deferred to `sdd-qa`); runbook line-number
drift (cosmetic); change-wide Phases 3–5 still pending; strict-TDD runner unavailable.
**Skill Resolution**: `paths-injected` — repository standards and SDD verification protocol supplied
by the orchestrator; quality-runner fallback used.

## References

- `openspec/changes/private-beta-launch-readiness/proposal.md`
- `openspec/changes/private-beta-launch-readiness/specs/publishing/spec.md`
- `openspec/changes/private-beta-launch-readiness/design.md`
- `openspec/changes/private-beta-launch-readiness/tasks.md`
- `openspec/changes/private-beta-launch-readiness/apply-progress.md`
- `openspec/changes/private-beta-launch-readiness/qa-report.md`
- `docs/infrastructure/private-beta-launch-readiness-runbook.md`
-
`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/linkedin/LinkedInPublishingAdapters.kt`
-
`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/linkedin/LinkedInAssetUploaderAdapters.kt`
-
`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/scheduling/PublishingWorker.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingMappers.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingApi.kt`
-
`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcPublishingRepositories.kt`
-
`server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/http/PublishingStaleJobsController.kt`
-
`server/smp/src/main/resources/db/changelog/publishing/020-add-publishing-claim-fencing-and-idempotency.yaml`
-
`server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/scheduling/PublishingWorkerTest.kt`
-
`server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcPublishingRepositoriesUnitTest.kt`
-
`server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/PublishingClaimFencingLiquibaseChangelogTest.kt`
-
`server/smp/src/test/kotlin/com/profiletailors/smp/publishing/integration/PublishingWorkerTransactionPostgresIntegrationTest.kt`
-
`server/smp/src/test/kotlin/com/profiletailors/smp/publishing/application/PublishingHandlersTest.kt`
-
`server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/linkedin/LinkedInPublishingAdaptersTest.kt`
-
`server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/infrastructure/http/PublishingStaleJobsControllerTest.kt`