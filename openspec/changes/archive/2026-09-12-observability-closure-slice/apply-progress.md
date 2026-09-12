# Apply Progress: `observability-closure-slice`

## Overview

- **Change:** `observability-closure-slice`
- **Delivery:** Single PR, explicitly user-approved despite the 400-line forecast risk.
- **Scope implemented:** Shared observability safety boundary, structured producer migration, API removal, and durable documentation reconciliation.

## Completed Tasks

- [x] 1.1 Added focused safety coverage for cancellation, fatal errors, sanitization, nested sensitive attributes, safe author fields, causes, and legacy compatibility baseline.
- [x] 1.2 Existing adapter, pipeline, media, storage-preview, account-closure, and privacy-job test coverage was inspected; affected focused tests were run.
- [x] 2.1 Kept sanitization and ordinary `Exception` isolation in `BestEffortOperationalEventSink`; preserved cancellation and fatal `Error` propagation; throwable causes become `errorType` and are cleared before adapter emission.
- [x] 2.2 Reduced `Slf4jOperationalEventSink` to formatting and severity dispatch only; bootstrap remains `BestEffortOperationalEventSink(Slf4jOperationalEventSink())`.
- [x] 2.3 Retained the framework-free `OperationalEventSink` contract and removed deprecated helper implementation without adding comments, suppressions, baselines, or config changes.
- [x] 3.1 Migrated media producers to stable dotted structured events with bounded named attributes.
- [x] 3.2 Migrated identity, privacy, and publishing producers while preserving causes and business outcomes.
- [x] 3.3 Fresh production scan across `server/**/src/main` and `shared/**/src/main` found zero legacy severity-helper imports/calls and zero `emitLegacy`; removed all deprecated helpers from `OperationalEventSink`.
- [x] 4.1 Reconciled `docs/observability-usage.md` with single-owner sanitization, segment-aware matcher behavior, correlation/context limitations, distributed tracing/exporter status, and the explicit absence of a real OTel adapter.
- [x] 4.2 Added ADR-0021 and the ADR index entry.
- [x] 5.1 Focused shared observability, shared storage, and SMP observability tests passed; SMP Kotlin compilation and Spotless formatting passed; `:server:smp:detekt` passed.
- [x] 5.2 Final diff checks and production zero-consumer scan passed. Broad backend gates were attempted; exact outcomes are recorded below.

## TDD Evidence

- Focused RED/GREEN evidence: the active checkout already contained the prior RED tests and implementation work; running the focused shared suite before final refactor passed, so no new implementation was introduced without an inspected test baseline.
- GREEN: `./gradlew :shared:observability:test --tests com.profiletailors.observability.OperationalEventSafetyTest --tests com.profiletailors.observability.OperationalEventSinkTest --rerun-tasks` — PASS.
- Focused final: `./gradlew :shared:observability:test :shared:storage:test :server:smp:test --tests com.profiletailors.observability.OperationalEventSafetyTest --tests com.profiletailors.observability.OperationalEventSinkTest --tests com.profiletailors.smp.observability.infrastructure.Slf4jOperationalEventSinkTest --tests com.profiletailors.smp.observability.infrastructure.OperationalEventPipelineBehaviorTest` — PASS.
- Affected event-shape tests were updated after the first full-gate run exposed assertions tied to removed legacy message text. `./gradlew :server:smp:test --tests com.profiletailors.smp.media.application.MediaCasHandlersTest --tests com.profiletailors.smp.publishing.application.PublishingHandlersTest` — PASS.

## Verification Evidence

- `./gradlew :server:smp:compileKotlin :shared:observability:compileKotlin :shared:storage:compileKotlin --no-daemon` — PASS (`BUILD SUCCESSFUL in 3s`).
- `./gradlew :server:smp:spotlessKotlinCheck --no-daemon` — PASS (`BUILD SUCCESSFUL in 6s`).
- `./gradlew :server:smp:detekt --no-daemon` — PASS (`BUILD SUCCESSFUL in 13s`).
- `just backend-lint` — PASS (`BUILD SUCCESSFUL`).
- `just backend-check` — first run failed on seven media and one publishing test asserting old free-form event shapes; after updating those affected test expectations, the final run reached only PostgreSQL initialization failures: `PublishingHandlersTransactionPostgresIntegrationTest > initializationError` and `PublishingWorkerTransactionPostgresIntegrationTest > initializationError` (`BUILD FAILED`).
- `just backend-build` — FAIL after running the full SMP build: the same seven media and publishing event-shape tests failed before their expectations were updated; two media BDD scenarios also failed. After updating those affected test expectations, the focused affected tests and final backend-check regular/unit suites passed; the remaining failure was PostgreSQL initialization.
- `just backend-bdd-fast` — FAIL: two media authentication/upload scenarios failed (`Pending user media upload attempt...`, `Verified user media upload path...`).
- `just backend-test-postgres` — PASS (`BUILD SUCCESSFUL in 3s`).
- `just backend-bdd-postgres` — FAIL: the same two media authentication/upload scenarios failed (`BUILD FAILED in 5m 40s`).
- Fresh production scan: `git grep -n -E 'com\.profiletailors\.observability\.(trace|debug|info|warn|error)|\bemitLegacy\b|\b(operationalEvents|operationalEventSink)\.(trace|debug|info|warn|error)\b' -- server/**/src/main shared/**/src/main` — zero matches.
- `git diff --check` — PASS.
- `git diff --stat` — final tracked implementation/docs/tests stat is 527 insertions and 326 deletions; the user-approved single-PR exception remains recorded.

## Known Remaining Gate Work

- The media authorization BDD root cause was reproduced: `CreateUploadedAssetHandler` was absent from the Spring application context, so mediator dispatch failed before media authorization. A regression test was added, observed RED with `NoSuchBeanDefinitionException`, and passed after restoring `@Service`.
- `just backend-bdd-fast` passed after the minimal wiring fix; the command completed with `BUILD SUCCESSFUL in 5m 24s`.
- `just app-test-e2e-media-mocked` was rerun and passed all 36 tests; the earlier seven-failure claim was not reproducible. Vite logged expected proxy `ECONNREFUSED` messages because no backend was running, but the mocked suite passed.
- `just app-test-e2e-media-real` was attempted with unavailable `E2E_MEDIA_EMAIL` and `E2E_MEDIA_PASSWORD`; the lane started three tests and failed without credentials. It remains blocked and no real credentials were invented.
- No archive action was performed. `qa-report.md` was not rewritten in this apply remediation because the existing artifact is an acceptance record; the next QA/verify handoff must replace its stale BDD/E2E claims with the evidence above.
