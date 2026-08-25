# Apply Progress: `apply-unit-2-publishing-controls`

## Overview

- **Change:** `private-beta-launch-readiness`
- **Scope:** DALLAY-555/557, Phase 2 publishing controls
- **Delivery:** Single PR with a user-approved size exception; the PR body records the review-budget rationale.

## Changes

### Completed in this apply slice

- Phase 2.1 stale-work repository, handler, and worker behavior is implemented and covered.
- Phase 2.2 safe-off/readiness configuration and the private-beta runbook are present; the runbook references `infra/apps/smp/swarm/stack.yaml`.
- Phase 2.3 now includes `GET /api/admin/publishing/stale-jobs`, platform permission enforcement, positive ISO-8601 threshold validation, bounded limits, safe response mapping, controller tests, and tagged Cucumber coverage.
- Cucumber hooks and step state use the stale-jobs response directly and do not reset away platform role assignments.
- Transaction rollback tests now distinguish durable pre-provider `IN_PROGRESS` attempts from rolled-back finalization writes; provider invocation and successful finalization were split into focused worker helpers.
- Coverage follow-up added worker tests for reconnect/ambiguous outcome fencing and retry-transition claim loss, plus repository tests for delivery-attempt phase/operation-key round trips, blocked transitions, stale arguments, and fenced updates.
- Critical publishing contract corrections are now implemented: provider response bodies, URLs, storage paths, and raw exception messages are excluded from diagnostics/logs; untyped provider failures map to `PUBLISHING_FAILED`; only `ProviderTransportUncertaintyException` maps to `AMBIGUOUS_OUTCOME`; list-publication mapping suppresses persisted technical messages; and stale reclaim preserves the durable operation key while advancing the claim fence.
- New regressions cover unknown-exception classification, typed transport uncertainty, provider result/upload redaction, list-response serialization safety, stale reclaim operation identity, and stale-recovery provider non-replay.

## Usage

### Verification evidence

- `SMP_DB_TEST_PASSWORD=test ./gradlew :server:smp:test --tests 'com.profiletailors.smp.publishing.infrastructure.scheduling.PublishingWorkerTest' --tests 'com.profiletailors.smp.publishing.infrastructure.scheduling.PublishingSchedulingConfigurationTest' --tests 'com.profiletailors.smp.publishing.application.PublishingHandlersTest' --tests 'com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcPublishingRepositoriesUnitTest' --tests 'com.profiletailors.smp.platformadmin.infrastructure.http.PublishingStaleJobsControllerTest' --tests 'com.profiletailors.smp.publishing.integration.PublishingQueueIntegrationTest' --no-daemon` — passed.
- `SMP_DB_TEST_PASSWORD=test ./gradlew :server:smp:postgresIntegrationTest --tests 'com.profiletailors.smp.publishing.integration.PublishingWorkerTransactionPostgresIntegrationTest' --tests 'com.profiletailors.smp.publishing.integration.PublishingQueuePostgresIntegrationTest' --no-daemon` — passed.
- `SMP_DB_TEST_PASSWORD=test ./gradlew :server:smp:test --tests 'com.profiletailors.smp.publishing.infrastructure.persistence.PublishingClaimFencingLiquibaseChangelogTest' --no-daemon` — passed.
- `SMP_DB_TEST_PASSWORD=test just backend-bdd-fast` — passed.
- `just backend-bdd-postgres` — passed.
- `just backend-test-fast` — passed.
- `SMP_DB_TEST_PASSWORD=test just backend-check` — passed.
- `just backend-build` — passed.
- `just backend-lint` — passed.
- `git diff --check` — passed.
- `:server:smp:spotlessKotlinCheck` — passed after applying the repository formatter.
- `SMP_DB_TEST_PASSWORD=test ./gradlew :server:smp:test --tests 'com.profiletailors.smp.publishing.infrastructure.scheduling.PublishingWorkerTest' --no-daemon` — passed (42 tests).
- `SMP_DB_TEST_PASSWORD=test ./gradlew :server:smp:test --tests 'com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcPublishingRepositoriesUnitTest' --no-daemon` — passed.
- RED evidence: the first focused run completed 155 tests with 4 expected failures in the new list-safety, stale-reclaim, unknown-exception, and upload-redaction regressions.
- `SMP_DB_TEST_PASSWORD=test ./gradlew :server:smp:test --tests 'com.profiletailors.smp.publishing.infrastructure.scheduling.PublishingWorkerTest' --tests 'com.profiletailors.smp.publishing.application.PublishingHandlersTest' --tests 'com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcPublishingRepositoriesUnitTest' --tests 'com.profiletailors.smp.publishing.infrastructure.linkedin.LinkedInPublishingAdaptersTest' --no-daemon` — passed after the red regressions and minimal fixes (203 tests).
- `SMP_DB_TEST_PASSWORD=test ./gradlew :server:smp:postgresIntegrationTest --tests 'com.profiletailors.smp.publishing.integration.PublishingWorkerTransactionPostgresIntegrationTest' --tests 'com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcPublishingRepositoriesTest' --no-daemon` — passed after stale-reclaim fencing fixes.
- `SMP_DB_TEST_PASSWORD=test just backend-bdd-fast` — passed after the publishing corrections.
- `SMP_DB_TEST_PASSWORD=test just backend-check` — passed after Spotless/Detekt refactoring; includes SMP unit and PostgreSQL integration gates.
- `SMP_DB_TEST_PASSWORD=test just backend-build` — passed, including `bootJar`, both BDD lanes, checks, tests, and Kover verification.
- `just backend-lint` — passed.
- `SMP_DB_TEST_PASSWORD=test ./gradlew :server:smp:koverXmlReport --no-daemon` — passed; ran SMP unit, fast BDD, Postgres BDD, and Postgres integration tasks. A local changed-production-line comparison reports 334/336 covered (99.40%); this is local evidence, not Codecov evidence.
- `git diff --check` — passed after the coverage follow-up.
- `:server:smp:detekt` — passed after the coverage follow-up.
- `com.profiletailors.smp.ModularStructureTest` — passed after moving the admin HTTP adapter into `platformadmin.infrastructure.http`.

## Troubleshooting

### Handoff and remaining work

- `state.yaml` is maintained by the orchestrator and now records Phase 2 verification and blocked QA.
- The existing `verify-report.md` is a pre-fix technical snapshot and still records the former critical findings; `sdd-verify` must regenerate it from this implementation. `qa-report.md` remains `BLOCKED` because production is running the pre-change `v0.4.1` release.
- No product/operator acceptance is claimed. An approved production release procedure and change window are required before rerunning acceptance QA.

## References

- `openspec/changes/private-beta-launch-readiness/tasks.md`
- `openspec/changes/private-beta-launch-readiness/verify-report.md`
- `openspec/changes/private-beta-launch-readiness/qa-report.md`
