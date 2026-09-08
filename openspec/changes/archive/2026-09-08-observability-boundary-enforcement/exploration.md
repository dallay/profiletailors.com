## Exploration: observability-boundary-enforcement

### Current State
`server/smp` enforcement is real but narrow. `HexagonalArchTest` imports only `com.profiletailors.smp` and bans `org.slf4j`, `ch.qos.logback`, `org.apache.logging.log4j`, `io.opentelemetry`, `io.micrometer` in `..domain..`/`..application..`, plus bans `com.profiletailors.observability` in `..domain..` only. `shared/*` has no equivalent observability ban: `StorageArchTest` and `RatelimitArchTest` check only domain/application/infrastructure direction, not framework imports.
`shared/observability` is framework-free (zero production dependencies) and exposes `OperationalEvent`, `Severity`, `OperationalEventSink` with preferred `emit(severity, name, ...)` plus legacy `info/warn/error` adapters. `docs/observability-usage.md` is the canonical norm and explicitly records two gaps: `Slf4jOperationalEventSink` does zero redaction, and correlation propagation is not implemented.
Verified violations: `StorageApplicationService` has 3 `logger.warn` calls on event-publish failure (upload/download/delete, each interpolating `bucket`/`key`); `GeneratePresignedUrlUseCase` has 1 equivalent warn; `RHSFilterParser` in `shared/presentation` under a `..domain..` package combines `LoggerFactory` (`log.error("Error parsing query: {}", query, e)` logging the full query map) with `tools.jackson.databind.ObjectMapper` in a domain-packaged class. No `server/smp` main-source caller of `RHSFilterParser`/`RHSFilterParserFactory` was found; live consumers are the two `shared/*` test suites. Zero productive `io.opentelemetry` imports confirmed in `server/smp` main and `shared`. Legitimate `..infrastructure..` Micrometer/SLF4J owners (`StorageMetrics`, `RateLimitMetrics`, `InvitationObservability`, `PasswordRecoveryObservability`) are correctly placed and out of scope for migration.

### Affected Areas
- `server/smp/src/test/kotlin/com/profiletailors/smp/HexagonalArchTest.kt` — current enforcement boundary; any rule change here must not weaken existing assertions per ARCH-001 governance.
- `shared/storage/src/main/kotlin/com/profiletailors/storage/application/StorageApplicationService.kt` — 3 slf4j warns to migrate; constructor currently `(storage, eventPublisher, metrics, provider)`.
- `shared/storage/src/main/kotlin/com/profiletailors/storage/application/GeneratePresignedUrlUseCase.kt` — 1 slf4j warn to migrate; constructor currently `(storage, eventPublisher, metrics, rateLimiter, ...)`.
- `shared/presentation/src/main/kotlin/com/profiletailors/common/domain/presentation/filter/RHSFilterParser.kt` — slf4j + Jackson in `..domain..` package; decision is migrate vs reclassify/move.
- `shared/spring-boot-common/src/main/kotlin/com/profiletailors/spring/boot/presentation/filter/RHSFilterParserFactory.kt` — Spring `@Component` bridge that keeps the parser alive despite no `server/smp` main caller.
- `shared/observability/src/main/kotlin/com/profiletailors/observability/OperationalEventSink.kt` — legacy `info/warn/error` adapters vs preferred structured `emit`; deprecation is a shared-contract compatibility decision.
- `server/smp/src/main/kotlin/com/profiletailors/smp/observability/infrastructure/Slf4jOperationalEventSink.kt` — zero-redaction sink; hardening target. Test `Slf4jOperationalEventSinkTest` proves only no-throw rendering, not redaction.
- `shared/storage/src/test/kotlin/com/profiletailors/storage/application/StorageApplicationServiceTest.kt`, `shared/storage/src/test/kotlin/com/profiletailors/storage/StorageUseCaseTest.kt` — ~12 construction sites per service plus `MockEventPublisher.shouldThrowOnPublish` swallow-and-continue semantics that migration must preserve.
- `shared/storage/build.gradle.kts`, `shared/presentation/build.gradle.kts` — neither depends on `:shared:observability` today; adding it is acyclic (observability has no outgoing deps) but is a new module edge needing dependency-graph documentation.
- `docs/observability-usage.md`, `docs/observability-contracts.md`, `docs/architecture/shared/dependencies.md`, `docs/architecture/adr/0010-shared-kernel-governance.md` — norm, Prometheus/SLO, module catalog, and framework-isolation owners that must stay consistent.

### Approaches
1. **Extend enforcement to shared/* via per-module ArchUnit rules** — add observability-import bans mirroring `HexagonalArchTest` to each existing `*ArchTest` (`StorageArchTest`, `RatelimitArchTest`) plus new ones for `presentation`/`spring-boot-common`/`observability`.
   - Pros: smallest blast radius; follows existing per-module precedent; each module stays self-contained.
   - Cons: N copies of the rule to drift; no single place proving full `shared/` coverage; `presentation` currently has no ArchTest at all.
   - Effort: Medium
2. **Extend enforcement via a common architecture-test module** — one shared test fixture or convention plugin asserting domain/application purity across all `shared/*` packages.
   - Pros: single rule definition; provable full coverage; natural home for future ARCH-00N shared concern.
   - Cons: new cross-module test wiring and Gradle plumbing; per architecture-governance policy a new check needs labelled output, clean baseline, and rollback plan, and must not become an unverified `just architecture-check` aggregator; risks duplicate Modulith-style redundancy debate.
   - Effort: High
3. **Migrate the 3 storage/application warns to `OperationalEventSink.emit(severity, name, ...)`** — inject sink (defaulted to `NoOpOperationalEventSink` to avoid breaking ~24 test construction sites at once), emit stable dotted names such as `storage.operation.event.publish.failed` with bounded attributes (`operation`, `provider`, sanitized `bucket`, no `key` or full payload), preserve swallow-and-continue plus `CancellationException` rethrow.
   - Pros: satisfies current `HexagonalArchTest` direction (application may depend on the observability port); removes slf4j from application; testable via captured events.
   - Cons: new `:shared:storage` to `:shared:observability` edge; event names/severities/attributes become a compatibility contract needing spec ownership; `bucket`/`key` cardinality and PII handling must be decided, not improvised per call site.
   - Effort: Medium
4. **Reclassify `RHSFilterParser` out of the domain package vs minimal migrate-in-place** — option 4a moves the class to an infrastructure/presentation package (or into `spring-boot-common` next to its factory) acknowledging Jackson+SLF4J are adapter concerns; option 4b keeps the package and swaps logging for sink emission plus narrows the logged value.
   - Pros (4a): resolves the Jackson-in-domain violation structurally, consistent with ADR-0010 framework isolation; Pros (4b): smaller diff, no import rewrites.
   - Cons (4a): package move ripples to factory, tests, and any external consumers; Cons (4b): Jackson-in-domain remains, contradicting the framework-free claim and inviting copy-paste precedent.
   - Effort: Medium (4a) / Low (4b)
5. **Harden `Slf4jOperationalEventSink` with key-allowlist/redaction** — strip attributes whose keys contain `token`, `password`, `secret`, `authorization`, `cookie`, `set-cookie`, `pii`, `email`, `otp` before rendering, per `observability-usage.md` recommended policy; add redaction unit tests for every severity including rendered messages and causes.
   - Pros: closes a documented PII-leak path at the boundary instead of relying on caller discipline; directly implements the documented norm.
   - Cons: redaction at render time cannot repair an already-unsafe `cause` chain or `message` text containing secrets; over-broad stripping may hide legitimate debugging keys; Prometheus/metric cardinality rules are separate and unaffected.
   - Effort: Medium
6. **Deprecate legacy `info/warn/error` sink adapters** — annotate with language-level `@Deprecated` pointing to structured `emit`, migrate in-repo callers (identity, media, privacy consumers per usage guide), leave behavior unchanged during the compatibility period.
   - Pros: stops growth of message-derived event names (`message.substringBefore(' ')`); steers new code to stable dotted names without breaking existing consumers.
   - Cons: deprecation warnings across many call sites turn green CI noisy; requires a recorded compatibility period and owner (Principal Architect per ADR-0010); must use `@Deprecated`, never prose or suppression.
   - Effort: Low (annotation) to High (full caller migration)

### Recommendation
Sequence the change as three gated steps: (a) per-module ArchUnit bans first with a deliberately failing-then-passing demonstration on the three known violations, deferring the common-module aggregator until baseline evidence exists; (b) migrate the storage/application warns to structured `emit` with defaulted sink injection and bounded, key-safe attributes; (c) reclassify `RHSFilterParser` out of `..domain..` rather than patching it in place, because Jackson-in-domain is structural, not cosmetic. Treat sink redaction and legacy-API deprecation as separate follow-ups behind their own compatibility decisions, not riders on the enforcement change.

### Risks
- Turning green CI red: new bans immediately fail on the 3+ known violations plus any undiscovered slf4j in `shared/*` application code (`StorageAutoConfiguration`, ratelimit gateways/filters also use slf4j but are in `..infrastructure..` and must be allowlisted, not flagged); land the migration before enabling the gate or gate as warning-first with a recorded flip date.
- Jackson-in-domain precedent: `RHSFilterParser` plus `SortParser`/`OffsetPagePresenter` show Jackson is load-bearing in presentation code; a narrow slf4j-only ban that ignores Jackson declares victory while leaving the framework-isolation violation intact.
- Prometheus semantics confusion: `StorageMetrics`/`RateLimitMetrics` bucket tags and SLO/SLI definitions in `observability-contracts.md` are metric contracts, not log events; an event-name cleanup must not rename metrics or claim SLI coverage.
- PII leaks via attributes and causes: current storage warns interpolate `bucket`/`key` and the parser logs the full query map; naive `emit` translation that copies those values into attributes recreates the leak in structured form, and the unredacted sink will render them.
- Over-scoping into a new `just architecture-check`: governance policy defers the unified gate; bundling it into this change risks rejection on process grounds even if the technical content is correct.

### Ready for Proposal
Yes, with five product/architecture decisions recorded first: (1) canonical event names, severities, and attribute keys for the storage publish-failure events, including whether `key` is ever emitted; (2) `RHSFilterParser` final package and whether Jackson stays avendor-neutral behind a port; (3) redaction key list and whether `cause` values are in scope; (4) legacy adapter deprecation period and owner; (5) per-module vs common-module enforcement home and the warning-first vs blocking rollout date. The orchestrator should tell the user this is a platform-governance change with no user-facing behavior, that `strict_tdd: true` in `openspec/config.yaml` is unreconciled with the init verdict (no workspace-wide single test command), and that proposal must not silently overwrite that config.
