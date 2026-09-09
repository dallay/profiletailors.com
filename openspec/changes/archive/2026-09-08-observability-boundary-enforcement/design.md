# Design: Observability Boundary Enforcement

## Technical Approach

Enforce ADR-0010/ADR-0002 in `shared/*` via per-module ArchUnit bans, migrating 4 storage publish-failure warns to structured `OperationalEventSink.emit` and reclassifying `RHSFilterParser` out of `..domain..`. Migration-first, bans-last; existing assertions untouched.

## Architecture Decisions

| Option | Tradeoff | Decision |
|---|---|---|
| Single `storage.operation.event.publish.failed` + `operation` attr vs 4 per-operation names | Single: bounded vocabulary, one docs row, attribute-filterable; per-op: grep-able but 4 contracts | Single name; `operation` reuses `StorageObservation.Operations` (`upload`/`download`/`delete`/`presign`) |
| Move parser to `spring.boot.presentation.filter` (SBC, next to factory) vs repackage inside presentation | SBC: removes Jackson/SLF4J from presentation entirely, matches proposal; in-place: smaller diff but keeps framework deps in shared-kernel module | Move to `com.profiletailors.spring.boot.presentation.filter.RHSFilterParser` in `:shared:spring-boot-common` |
| Bans per-module blocking migrate-then-enable vs warning-first gate | Warning-first delays enforcement; migrate-first gives fail-then-pass proof without red CI | Migrate → enable per module; order: storage, presentation, ratelimit |
| Defaulted `NoOpOperationalEventSink` trailing param vs required param / overloads | Required breaks ~24 test call sites; overloads duplicate logic | Trailing `operationalEvents: OperationalEventSink = NoOpOperationalEventSink`; positional calls keep compiling |

`SortParser`/`OffsetPagePresenter` scope: verified in `com.profiletailors.spring.boot.presentation.*` with no `..domain..`/`..application..` segment — outside ban scope. No widening; no change.

## Data Flow

```
StorageApplicationService / GeneratePresignedUrlUseCase (application)
  ──emit(WARN, storage.operation.event.publish.failed, op/provider/bucket)──→ OperationalEventSink (port, :shared:observability)
  ──publish(domain event)──→ EventPublisher ──→ infrastructure
  swallow-and-continue; CancellationException rethrows, no emit
```

## File Changes

| File | Action | Description |
|---|---|---|
| `shared/storage/.../application/StorageApplicationService.kt` | Modify | 3 warns → `emit`; add defaulted sink param; drop SLF4J import |
| `shared/storage/.../application/GeneratePresignedUrlUseCase.kt` | Modify | 1 warn → `emit`; add defaulted sink param; drop SLF4J import |
| `shared/presentation/.../domain/presentation/filter/RHSFilterParser.kt` | Delete | Moved out of `..domain..` |
| `shared/spring-boot-common/.../presentation/filter/RHSFilterParser.kt` | Create | Same logic, new package; no full-query-map log/emit |
| `shared/spring-boot-common/.../presentation/filter/RHSFilterParserFactory.kt` | Modify | Import rewrite (same package post-move, import dropped) |
| `shared/presentation/.../filter/RHSFilterParserTest.kt` | Move | To SBC test tree with updated package/import |
| `shared/storage/.../StorageArchTest.kt` | Modify | Additive ban rules only |
| `shared/shield/ratelimit/.../RatelimitArchTest.kt` | Modify | Additive ban rules only |
| `shared/presentation/src/test/.../PresentationArchTest.kt` | Create | New ban test for presentation module |
| `shared/storage/build.gradle.kts` | Modify | Add `implementation(project(":shared:observability"))` |
| `shared/storage/.../StorageApplicationServiceTest.kt`, `StorageUseCaseTest.kt`, `StorageAutoConfiguration.kt`, smp test bases | Modify | ~24 construction sites keep compiling via default; add captured-sink cases |
| `docs/observability-usage.md`, `docs/observability-contracts.md`, `docs/architecture/shared/dependencies.md` | Modify | Event name/attrs + storage→observability edge |

Ban rules (additive, blocking, existing assertions unchanged):

- `domainShouldNotDependOnObservabilityFrameworks`: `..domain..` must not depend on `org.slf4j..`, `ch.qos.logback..`, `org.apache.logging.log4j..`, `io.opentelemetry..`, `io.micrometer..`, `tools.jackson..`, `com.fasterxml.jackson..`, `com.profiletailors.observability..`.
- `applicationShouldNotDependOnObservabilityImplementations`: `..application..` must not depend on the same set minus `com.profiletailors.observability..` (application may use the sink port).
- Infrastructure owners (`StorageMetrics`, `StorageAutoConfiguration`, `S3RetryHelper`, `RateLimitMetrics`, `RateLimitingFilter`, `Bucket4jRateLimiter`, `SpringRateLimitEventPublisher`, `BucketConfigurationFactory`, SBC Jackson/Spring adapters, post-move `RHSFilterParser`/`SortParser`/`OffsetPagePresenter`) stay allowlisted by scope — rules match only `..domain..`/`..application..`. No SBC ArchTest: SBC has no `..domain..`/`..application..` packages.

## Interfaces / Contracts

```kotlin
class StorageApplicationService(
    private val storage: Storage,
    private val eventPublisher: EventPublisher<BaseDomainEvent>,
    private val metrics: StorageObservation,
    private val provider: String = StorageObservation.Providers.LOCAL,
    private val operationalEvents: OperationalEventSink = NoOpOperationalEventSink,
)
```

Event contract:

| Name | Severity | Attributes | Rules |
|---|---|---|---|
| `storage.operation.event.publish.failed` | WARN | `operation`, `provider`, `bucket` + `cause` | `key`/payload/metadata/expiry/requesterId never emitted; message is constant text with no interpolated values; `bucket` is the validated bucket string only (`validateBucketAndKey` already rejects `..`; blank never emitted — skip attribute); `cause` is the original publish `Throwable` |

Gradle edge: `:shared:observability` has zero production deps — `:shared:storage → :shared:observability` is acyclic by construction. Presentation Jackson/SLF4J `implementation` deps become unused post-move; verify and remove.

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Unit (storage) | 4 emit sites: name, WARN, attrs, no `key`, swallow-and-continue, cancellation rethrow | Captured fake sink; failing publisher + `CancellationException` cases |
| Unit (parser) | Moved parser + factory resolve, no full-query-map output | Existing parser tests relocated to SBC; failure-path asserts no query dump |
| Architecture | Bans fail pre-migration, pass post-migration | Storage + presentation fail-then-pass runs; ratelimit pass-only (no app-layer violation); no weakened assertions |
| Integration | `StorageAutoConfiguration` wiring, Gradle edge compiles | Bean test with defaulted sink; `./gradlew :shared:storage:build` |
| Docs | Names/attrs/edge match code | Reviewer diff of 3 docs vs implementation |

Zero-comment policy holds; Detekt/ArchUnit baselines unchanged.

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or process-integration boundary.

## Migration / Rollout

No data migration, no flags. Land migration + tests, then enable bans per-module (storage → presentation → ratelimit). Rollback reverse-order: bans, warns, parser move, Gradle edge; each step compiles. Out of scope: sink redaction hardening, legacy `info`/`warn`/`error` deprecation, common aggregator module, `just architecture-check`, metric/SLO renames.

## Open Questions

- None blocking. Inputs for tasks phase (not decided here): `chain_strategy` undecided; `strict_tdd` drift unresolved — `openspec/config.yaml` untouched.
