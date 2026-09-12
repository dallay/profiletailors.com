# Exploration: Final Observability Closure Slice

## Current State

The repository has a pure-Kotlin `shared/observability` contract centered on `OperationalEventSink.emit(OperationalEvent)`, plus `NoOpOperationalEventSink`, a structured convenience overload, `BestEffortOperationalEventSink`, and `OperationalEventSanitizer`. The SMP bootstrap currently wires `BestEffortOperationalEventSink(Slf4jOperationalEventSink())` as the default sink.

`BestEffortOperationalEventSink` currently sanitizes and catches ordinary `Exception` failures while preserving `CancellationException`. `Slf4jOperationalEventSink` independently sanitizes and also catches ordinary failures, so failure isolation is duplicated across the decorator and adapter. The sanitizer currently treats any key containing broad fragments such as `auth`, `email`, and `pii` as sensitive; this protects secrets but can over-redact ordinary operational attributes (especially keys containing `auth` as a non-secret word). Existing tests cover nested sensitive-key families and safety behavior, but do not cover the desired narrower matcher boundary.

`OperationalEventSink.kt` still contains deprecated severity helpers (`trace`, `debug`, `info`, `warn`, `error`) backed by private `emitLegacy`. A repository-wide production scan found remaining callers in SMP application code, primarily media, privacy, and identity handlers/jobs. The current helper caller set includes `AssetPreviewUrlResolver`, `MediaAssetBackfillJob`, `MediaHandlers`, `StaleAssetReconciler`, `CloseAccountHandler`, `CloseAccountOrchestrator`, and `FindExpiredRequestsJob`; imports of the deprecated extensions identify the exact production files. `shared/storage` is already using structured events through `StorageOperationalEvents` in `GeneratePresignedUrlUseCase` and `StorageApplicationService`, with tests asserting the contract.

The current observability documentation explicitly describes the legacy migration as incomplete and says compatibility extensions must be removed after remaining call sites migrate. It also documents the duplicated sanitizer behavior and current SLF4J adapter. The repository's OpenSpec README states that purely technical refactors, architecture governance, test-harness plans, and quality-gate work do not belong as standalone OpenSpec capabilities. Nevertheless, the orchestrator requires an SDD change artifact; this exploration therefore uses an active technical change folder under `openspec/changes/` without creating a product capability spec.

No uncommitted worktree changes were present at exploration start. The active branch is `observability`, based on the observability boundary work in commit `4f6d0507` (`Enforce safe vendor-neutral observability boundaries`). No existing active OpenSpec change or state file covers this final observability closure slice.

## Affected Areas

- `shared/observability/src/main/kotlin/com/profiletailors/observability/OperationalEventSink.kt` — migrate all production callers from deprecated severity helpers, then remove the deprecated extensions and private `emitLegacy` only after a production-wide zero-call-site proof.
- `shared/observability/src/main/kotlin/com/profiletailors/observability/BestEffortOperationalEventSink.kt` — retain the sole ordinary-failure isolation boundary and define cancellation propagation clearly.
- `server/smp/src/main/kotlin/com/profiletailors/smp/observability/infrastructure/Slf4jOperationalEventSink.kt` — remove independent swallowing and likely adapter-side sanitization once the outer boundary is authoritative; keep formatting and severity mapping adapter responsibilities.
- `shared/observability/src/main/kotlin/com/profiletailors/observability/OperationalEventSanitizer.kt` — refine sensitive-key matching to distinguish secret-bearing authentication keys from safe operational keys, while preserving case-insensitive and nested-key protection.
- `server/smp/src/main/kotlin/com/profiletailors/smp/observability/infrastructure/ObservabilityBootstrapConfiguration.kt` — verify/wire the single best-effort isolation boundary around the adapter.
- `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/AssetPreviewUrlResolver.kt` — migrate legacy debug/warn calls to structured events with explicit names and attributes.
- `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/MediaAssetBackfillJob.kt` — migrate legacy info/warn/error calls and preserve failure causes/operational attributes.
- `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/MediaHandlers.kt` — migrate legacy info/warn/error calls, including argument-bearing and failure calls.
- `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/StaleAssetReconciler.kt` — migrate legacy debug/info/warn/error calls, including throwable-last legacy patterns.
- `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/CloseAccountHandler.kt` — migrate remaining legacy info call.
- `server/smp/src/main/kotlin/com/profiletailors/smp/privacy/application/CloseAccountOrchestrator.kt` — migrate legacy debug/info calls.
- `server/smp/src/main/kotlin/com/profiletailors/smp/privacy/application/FindExpiredRequestsJob.kt` — migrate legacy error/info calls.
- `shared/observability/src/test/kotlin/com/profiletailors/observability/OperationalEventSafetyTest.kt` — add sanitizer boundary tests for safe authentication-related keys and still-sensitive credential/token keys; test best-effort isolation and cancellation behavior.
- `shared/observability/src/test/kotlin/com/profiletailors/observability/OperationalEventSinkTest.kt` — remove legacy-helper compatibility coverage and strengthen structured-event contract coverage.
- `server/smp/src/test/kotlin/com/profiletailors/smp/observability/infrastructure/Slf4jOperationalEventSinkTest.kt` — verify adapter failures are not swallowed by the adapter itself and that adapter formatting receives already-safe events.
- `shared/storage/src/test/kotlin/com/profiletailors/storage/StorageUseCaseTest.kt` and `shared/storage/src/test/kotlin/com/profiletailors/storage/application/StorageApplicationServiceTest.kt` — preserve existing structured storage-event assertions while checking no legacy dependency is introduced.
- `docs/observability-usage.md` — document final structured-event-only migration state, single-owner failure isolation, refined redaction policy, and explicit out-of-scope tracing/OpenTelemetry status.
- `docs/observability-contracts.md` — reconcile the architecture contract with the final adapter/decorator ownership and safety behavior.
- `docs/architecture/adr-enforcement/ADR-0002.md` and related architecture references — inspect for any claim affected by the observability boundary; update only if the final technical decision changes the durable architecture contract.
- `openspec/changes/observability-closure-slice/explore.md` — required exploration artifact for this orchestrated technical SDD slice.

## Approaches

1. **Single boundary decorator with structured-only producers** — keep `OperationalEventSink` as the pure contract, make `BestEffortOperationalEventSink` the only sanitizer/failure-isolation boundary, make `Slf4jOperationalEventSink` a transparent formatting/logging adapter, migrate every production helper caller to `emit(severity, stableName, message, cause, named attributes)`, then delete compatibility helpers.
   - Pros: one clear policy boundary; adapter failures remain observable to the decorator; structured events are portable to future exporters; removal is mechanically provable; matches existing bootstrap wiring and architecture rules.
   - Cons: touches several media/privacy/identity files; event names and attributes must be reviewed individually; tests must distinguish policy from adapter behavior.
   - Effort: Medium

2. **Keep defensive adapter sanitization and swallowing while also tightening the decorator** — migrate callers and remove legacy helpers but preserve duplicate safety in `Slf4jOperationalEventSink`.
   - Pros: direct adapter construction remains defensive; smaller immediate adapter change.
   - Cons: violates the requested single-owner isolation model; keeps policy duplicated and can hide adapter defects; future adapters may copy inconsistent behavior.
   - Effort: Medium

3. **Move all sanitization and isolation into the SLF4J adapter** — make the adapter own safety and leave the decorator as a pass-through.
   - Pros: implementation is locally simple for the current backend.
   - Cons: breaks vendor-neutral shared boundary intent; custom sinks/exporters would not inherit safety; contradicts the requested ownership and makes shared tests less meaningful.
   - Effort: Medium

## Recommendation

Use Approach 1. Treat `BestEffortOperationalEventSink` as the sole policy/degradation boundary: sanitize once, rethrow cancellation, swallow ordinary sink failures, and do not let `Slf4jOperationalEventSink` swallow or independently sanitize. Keep the SLF4J adapter responsible only for severity mapping and rendering a trusted/sanitized event.

Migrate every production legacy helper call to explicit structured events before deleting the deprecated extensions. The migration should choose stable dotted names per operation, move identifiers into named attributes, attach throwable causes explicitly, and avoid exposing secrets or payloads. A final repository-wide production search should prove no imports or calls to the deprecated helpers remain; test-only compatibility calls should also be removed or intentionally retained only if the contract explicitly requires them. Since the work is purely technical, persist the required orchestrator-requested exploration artifact but do not invent a standalone product capability spec.

For sensitive-key matching, replace broad substring matching for `auth` with token/segment-aware matching and an explicit set of credential-bearing names/fragments. Preserve redaction/removal for clear secret fields such as authorization headers, bearer/token fields, passwords, secrets, API keys, cookies, credentials, and OTPs. Add positive tests for safe operational keys containing ordinary authentication language and negative tests for each protected family. The exact matcher vocabulary should be finalized in design/spec review against all existing event attributes and the security contract.

Explicitly keep correlation identifiers/tracing propagation and a real OpenTelemetry exporter/implementation out of scope. Documentation should state that this slice completes the structured operational-event boundary only; it does not implement tracing context, correlation propagation, metrics exporters, or OpenTelemetry.

## Risks

- Event-name/attribute migration can accidentally change searchable operational semantics if names are invented inconsistently; establish a naming table and preserve existing message meaning in structured fields.
- Removing `emitLegacy` before a complete production and test scan could break compilation or leave hidden source-set consumers; use repository-wide searches plus focused compilation/tests before deletion.
- Narrowing `auth` matching can create a security regression if a credential-bearing key is not included; retain explicit protected families and add adversarial tests for casing, nesting, separators, and values embedded in messages.
- Removing adapter swallowing means direct adapter tests or non-bootstrap construction will now surface failures; this is intentional, but all production wiring must remain through `BestEffortOperationalEventSink`.
- Existing documentation currently states duplicated sanitizer behavior and an incomplete migration; stale wording would contradict the implementation and must be updated in the same change.
- The repository OpenSpec policy excludes purely technical work from standalone product capabilities, while the orchestrator explicitly requires SDD artifacts. Keep the artifact limited to exploration/design evidence and do not add a fake product requirement.
- Full verification spans shared observability, shared storage, and SMP backend tests, Detekt/architecture checks, backend build/check, and possibly broader CI recipes; Docker/Testcontainers or environment-specific checks may be unavailable.

## Ready for Proposal

Yes. The orchestrator can proceed to proposal/design with a technical-change framing, explicitly recording that no standalone product capability spec is created. The proposal should define the single isolation owner, the exact legacy-call-site migration inventory, the sanitizer matcher contract and tests, documentation reconciliation, out-of-scope tracing/OpenTelemetry, and the complete verification plan.
