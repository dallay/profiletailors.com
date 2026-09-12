# Tasks: Observability Closure Slice

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 420–560 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 boundary/tests → PR 2 caller migration/API removal → PR 3 docs/gates |
| Delivery strategy | single-pr |
| Chain strategy | single-pr |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: single-pr (user-approved)
400-line budget risk: Accepted by user

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Safety boundary and RED tests | PR 1 | Shared decorator/sanitizer plus adapter tests; independently verifiable. |
| 2 | Structured caller migration and API deletion | PR 2 | Depends on Unit 1; migrate all seven caller groups, scan, then delete helpers. |
| 3 | Contract docs and final gates | PR 3 | Depends on Unit 2; docs, ADR, focused and full verification. |

## Explicit Non-goals

- [x] Do not add correlation/request-context propagation, distributed tracing, exporters, metrics, or a real OpenTelemetry adapter.
- [x] Do not change business exception propagation, handler outcomes, persisted/wire contracts, or unrelated SLF4J logging.
- [x] Do not add product capabilities or promote this technical spec to `openspec/specs/`.

## Phase 1: RED Tests / Contract Baseline

- [x] 1.1 In `shared/observability/src/test/kotlin/com/profiletailors/observability/OperationalEventSafetyTest.kt` and `OperationalEventSinkTest.kt`, add RED tests for ownership, cancellation/`Error`, causes, nested secrets, safe `author`/`authorship`, redaction, and helper removal.
- [x] 1.2 In `Slf4jOperationalEventSinkTest.kt`, `OperationalEventPipelineBehaviorTest.kt`, `MediaAssetBackfillJobTest.kt`, `StorageAssetPreviewUrlResolverTest.kt`, `CloseAccountOrchestratorTest.kt`, and `FindExpiredRequestsJobTest.kt`, add RED adapter, event-shape, and outcome assertions.

## Phase 2: GREEN Core Boundary

- [x] 2.1 Update `BestEffortOperationalEventSink.emit` and `OperationalEventSanitizer.sanitize` to own one locale-stable sanitize/degrade boundary, preserve `CancellationException`/`Error`, and clear cause safely.
- [x] 2.2 Update `Slf4jOperationalEventSink` to formatting/severity dispatch only; verify `ObservabilityBootstrapConfiguration` wires `BestEffortOperationalEventSink(Slf4jOperationalEventSink())`.
- [x] 2.3 Refactor only after RED tests pass: retain framework-free `OperationalEventSink`, zero comments/suppressions, and rerun focused shared/SMP tests.

## Phase 3: Structured Migration and API Removal

- [x] 3.1 Migrate `AssetPreviewUrlResolver`, `MediaAssetBackfillJob`, `MediaHandlers`, and `StaleAssetReconciler` to stable dotted `media.*` events and bounded named attributes.
- [x] 3.2 Migrate `CloseAccountHandler`, `CloseAccountOrchestrator`, and `FindExpiredRequestsJob` to `identity.*`/`privacy.*` events while preserving causes and business outcomes.
- [x] 3.3 Migrate remaining SMP callers such as publishing preview resolution, perform fresh production scans under `server/**/src/main` and `shared/**/src/main`, then remove `trace`, `debug`, `info`, `warn`, `error`, and `emitLegacy` from `OperationalEventSink`.

## Phase 4: Documentation and Architecture

- [x] 4.1 Update `docs/observability-usage.md` and affected observability documentation with the final safety boundary, event naming, matcher scope, correlation/context status, tracing/exporter status, and real OTel adapter non-goals.
- [x] 4.2 Add ADR-0021 and its ADR index entry; keep the technical specification only under this active change and do not add product specs.

## Phase 5: Verification / Full Gates

- [x] 5.1 Run focused shared observability, shared/storage, and SMP tests plus Kotlin compilation and formatting.
- [x] 5.2 Run Detekt/backend-lint, backend-check, backend-build, backend-bdd-fast, PostgreSQL integration, PostgreSQL BDD where environment permits, final diff checks, and fresh zero-consumer scan; record exact results and pre-existing/environment failures in apply-progress.
