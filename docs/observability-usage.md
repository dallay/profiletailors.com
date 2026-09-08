# Shared Observability Usage Standard

**Last Updated:** 2026-09-08  
**Status:** Active documentation standard  
**Scope:** Kotlin shared modules and the SMP backend  
**Audience:** Backend engineers, platform engineers, operations, SRE, and reviewers

## Purpose and status

This guide is the canonical usage standard for `shared/observability` and the SMP
`observability` bounded context. It has two responsibilities:

1. Record what the repository implements today.
2. Define the recommended norm for new or changed code.

The labels **Implemented** and **Recommended** are deliberate. A recommendation in this guide is
not evidence that runtime enforcement exists. This documentation-only change does not add a sink,
exporter, redaction enforcement, correlation propagation, frontend telemetry, or API change.

The contract tables below are reconciled directly against the Kotlin sources and tests linked in
each section; this guide is self-contained and needs no additional change record to be applied.

## Quick decision guide

| Situation | Use | Do not do |
| --- | --- | --- |
| A domain or application operation needs an operational event | Inject `OperationalEventSink` and emit a structured `OperationalEvent` | Import SLF4J, Micrometer, or OpenTelemetry directly into a handler |
| A component needs a no-op default for a focused unit test or optional background job | `NoOpOperationalEventSink` | Create a second ad hoc no-op contract |
| A new event is needed | A stable dotted `name`, a suitable `Severity`, and bounded attributes | Put secrets, personal values, request payloads, or unbounded user text in attributes |
| A failure crosses the handler or mediator boundary | Preserve the original `Throwable` as `cause`, emit the failure event, and rethrow the failure | Replace the cause with a string or make observability swallow the business failure |
| A frontend needs telemetry | Use a web-owned contract and implementation | Import `com.profiletailors.observability` from `apps/web/**` or `shared/web/**` |
| An SLO, SLI, or latency target needs to change | Update the owner document, [Observability Contracts & SLA Matrix](./observability-contracts.md) | Treat an operational log event as an SLO definition |

## Implemented shared contract

### `OperationalEvent`

Source: [`shared/observability/src/main/kotlin/com/profiletailors/observability/OperationalEvent.kt`](../shared/observability/src/main/kotlin/com/profiletailors/observability/OperationalEvent.kt)

```kotlin
data class OperationalEvent(
    val name: String,
    val severity: Severity,
    val message: String? = null,
    val attributes: Map<String, Any?> = emptyMap(),
    val cause: Throwable? = null,
)
```

The current fields mean:

- `name`: the event identity. The current implementation accepts any `String`; the recommended
  format is a stable dotted name such as `media.asset.preview.fallback`.
- `severity`: one value from `Severity`.
- `message`: optional human-readable text. It is not a replacement for the event name or
  structured attributes.
- `attributes`: optional key/value metadata. The current type accepts `Any?`, so callers must
  apply the safe-value and cardinality rules in this guide themselves.
- `cause`: optional original `Throwable`. Attach it to failure events when the failure itself is
  observable and useful to the operational sink.

### `OperationalEventSink`

Source: [`shared/observability/src/main/kotlin/com/profiletailors/observability/OperationalEventSink.kt`](../shared/observability/src/main/kotlin/com/profiletailors/observability/OperationalEventSink.kt)

```kotlin
fun interface OperationalEventSink {
    fun emit(event: OperationalEvent)
}
```

The shared module also implements the following no-op sink:

```kotlin
object NoOpOperationalEventSink : OperationalEventSink {
    override fun emit(event: OperationalEvent) = Unit
}
```

The structured convenience extension has this exact signature:

```kotlin
fun OperationalEventSink.emit(
    severity: Severity,
    name: String,
    message: String? = null,
    cause: Throwable? = null,
    vararg attributes: Pair<String, Any?>,
)
```

It constructs an `OperationalEvent` and converts the attribute pairs with `toMap()`. Duplicate
attribute keys therefore follow Kotlin map construction behavior; callers should not provide
ambiguous duplicate keys.

The legacy severity convenience extensions remain implemented:

```kotlin
fun OperationalEventSink.trace(message: String, vararg arguments: Any?)
fun OperationalEventSink.debug(message: String, vararg arguments: Any?)
fun OperationalEventSink.info(message: String, vararg arguments: Any?)
fun OperationalEventSink.warn(message: String, vararg arguments: Any?)
fun OperationalEventSink.error(message: String, vararg arguments: Any?)
```

Their current adapter behavior is important when reading existing consumers:

- The event name is `message.substringBefore(' ')`.
- If the last argument is a `Throwable`, it becomes `cause` and is removed from the argument list.
- Other arguments are stored as `argument.0`, `argument.1`, and so on.
- The original message is retained as `message`.

New code SHOULD prefer the structured overload because the name and attribute keys are explicit.
Existing legacy calls must not be interpreted as evidence that message text is a stable event
schema.

### `Severity`

Source: [`shared/observability/src/main/kotlin/com/profiletailors/observability/Severity.kt`](../shared/observability/src/main/kotlin/com/profiletailors/observability/Severity.kt)

```kotlin
enum class Severity {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
}
```

The current enum has no `FATAL` value. A severity change is a contract decision, not a local
formatting preference; see [Evolution and versioning](#evolution-and-versioning).

### `RequestOutcome`

Source: [`shared/common/src/main/kotlin/com/profiletailors/common/domain/observability/RequestOutcome.kt`](../shared/common/src/main/kotlin/com/profiletailors/common/domain/observability/RequestOutcome.kt)

```kotlin
@ValueObject
enum class RequestOutcome {
    SUCCESS,
    FAILURE,
}
```

`RequestOutcome` is in `shared:common`, not `shared:observability`. It is used by the SMP
observability and audit hook contracts:

```kotlin
fun interface MetricsHook {
    suspend fun onRequestHandled(requestName: String, outcome: RequestOutcome)
}

fun interface RateLimitHook {
    suspend fun onRequestReceived(requestName: String)
}
```

The current SMP registry is:

```kotlin
class ObservabilityHookRegistry(
    val metricsHook: MetricsHook,
    val rateLimitHook: RateLimitHook,
)
```

The actual constructor is on one line in the source, but the signature above is equivalent and
shows the two public properties. `ObservabilityBootstrapConfiguration` supplies
`NoOpMetricsHook` and `NoOpRateLimitHook` when no implementation is present. The hooks are
separate from `OperationalEventSink`; do not assume that registering a hook creates an exporter
or a metric.

## Current SMP pipeline and consumers

### Request pipeline

The implemented SMP pipeline is

```text
mediator request
    -> OperationalEventPipelineBehavior
    -> OperationalEventSink
    -> Slf4jOperationalEventSink
    -> SLF4J logger profiletailors.operational
```

Source: [`OperationalEventPipelineBehavior.kt`](../server/smp/src/main/kotlin/com/profiletailors/smp/observability/infrastructure/OperationalEventPipelineBehavior.kt)

`OperationalEventPipelineBehavior` is a Spring `@Component` implementing the shared bus
`PipelineBehavior`. It receives an `OperationalEventSink` and currently emits:

| Event name | Severity | Current attributes | Current outcome |
| --- | --- | --- | --- |
| `bus.request.started` | `INFO` | `request` | Emitted before the handler delegate runs |
| `bus.request.completed` | `INFO` | `request`, `durationMs` | Emitted after a successful delegate |
| `bus.request.failed` | `ERROR` | `request`, `durationMs` | Emitted after a failed delegate with the original `cause` |

The `request` value is the request class simple name, or `anonymous` when the request is null.
`durationMs` is calculated from `System.nanoTime()`. A failed request is rethrown with
`getOrThrow()`, so the pipeline does not turn an operational event into a successful request.

Source: [`Slf4jOperationalEventSink.kt`](../server/smp/src/main/kotlin/com/profiletailors/smp/observability/infrastructure/Slf4jOperationalEventSink.kt)

The current sink maps the five `Severity` values to the corresponding SLF4J method and passes
`event.cause` as the logger cause. It renders the optional message and named attributes as text.
Keys beginning with `argument.` are used for message placeholder rendering and are excluded from
the appended named-attribute segment. The current sink catches `Exception` while formatting or
logging and does not rethrow that sink failure. It does not implement secret or personal-data
redaction.

### Existing consumers

Current `OperationalEventSink` consumers are concentrated in SMP application services and
infrastructure wiring:

- Identity application: `CloseAccountHandler` and `ResetPasswordHandler`.
- Media application: `AssetPreviewUrlResolver`, `MediaAssetBackfillJob`,
  `MediaHandlers`, and `StaleAssetReconciler`.
- Privacy application: `CloseAccountOrchestrator` and `FindExpiredRequestsJob`.
- Media infrastructure: `MediaApplicationConfiguration` wires the sink into media application
  services.
- SMP observability infrastructure: `OperationalEventPipelineBehavior` consumes the sink and
  `Slf4jOperationalEventSink` implements it.

Some of these consumers use the legacy `info`, `warn`, `debug`, and `error` extensions, while the
pipeline and selected handlers construct structured events. This is an observed mixed state, not
a reason to add more message-derived schemas.

The module dependency is declared by `server:smp` through
`implementation(project(":shared:observability"))` in
[`server/smp/build.gradle.kts`](../server/smp/build.gradle.kts). No production source in
`apps/web/**` or `shared/web/**` imports `com.profiletailors.observability`.

## Recommended backend layering

The recommended norm follows [ADR-0002: Adhere to Hexagonal Architecture](./architecture/adr/0002-adhere-to-hexagonal-architecture.md):

```text
domain -> application port or shared sink abstraction -> infrastructure binding
```

### Domain

**Recommended:** Domain policies and models remain framework-free. If a domain operation genuinely
needs to publish an operational fact, depend on an inward-facing abstraction and pass only domain
safe values. Prefer an application-level port when the event is a use-case concern.

**Not implemented as a universal rule:** Current usage is mixed and the shared
`OperationalEventSink` is directly consumed by application code. This guide does not claim that
all existing handlers have been migrated to a separate local port.

### Application

**Recommended:** Application handlers may receive an `OperationalEventSink` or a narrower
application port through constructor injection. They emit operational facts, not infrastructure
records. A handler MUST NOT directly use SLF4J, Micrometer, or OpenTelemetry for this purpose.

Use the structured form for new events:

```kotlin
operationalEvents.emit(
    severity = Severity.INFO,
    name = "media.asset.preview.fallback",
    message = "Using the local preview endpoint",
    "assetId" to "asset-demo-001",
    "provider" to "local",
)
```

The example uses an opaque synthetic identifier, a stable dotted name, and bounded attributes. It
contains no request payload, credential, email address, authorization value, cookie, or other
personal value.

### Infrastructure

**Recommended:** Infrastructure owns the binding from the application abstraction to a concrete
sink or exporter. The current SMP binding is `Slf4jOperationalEventSink`; do not add a new
framework dependency to the shared Kotlin contract to make a binding convenient.

Infrastructure configuration may use Spring. Application and domain code must not import the
infrastructure binding. The architecture boundaries remain those described in the
[architecture overview](./architecture/) and [C4 code model](./architecture/c4/04-code.md).

## Kotlin-only web boundary

**Implemented:** `shared/observability` is a Kotlin Gradle module. The repository currently has no
web consumer of `com.profiletailors.observability`.

**Recommended:** Keep the module Kotlin-only and framework-free. `apps/web/**` and `shared/web/**`
MUST NOT import it. Frontend telemetry, browser consent, and frontend error reporting have separate
contracts and ownership. A web requirement must not be solved by exposing Kotlin classes through a
shared TypeScript package.

## Event naming and attributes

### Stable names

**Recommended:** Use lowercase dotted names with a stable meaning and a bounded vocabulary, for
example:

- `bus.request.started`
- `bus.request.completed`
- `bus.request.failed`
- `media.asset.preview.fallback`
- `identity.reset.failed`

Treat `name` as a compatibility key. Do not include an ID, email, URL, timestamp, exception text,
request input, or other high-cardinality value in the name. Do not silently rename an event or reuse
an existing name for a different meaning.

### Safe attributes

Attributes SHOULD be additive and low-cardinality. Prefer bounded enums, booleans, durations,
counts, operation names, provider names, and opaque internal identifiers when an identifier is
necessary for investigation. Do not emit raw request bodies, query strings, headers, access tokens,
refresh tokens, passwords, cookies, email addresses, free-form user text, or full exception
messages as attributes.

This is an operational-event rule, not a promise about every existing log statement in the
repository. Review adjacent logging and telemetry code when adding a new event.

## Sensitive data and the current redaction gap

### Recommended sink policy

Every future or replaced sink MUST remove any attribute whose key contains one of these
secret-like or personal-data terms before emission:

- `token`
- `password`
- `secret`
- `authorization`
- `cookie`
- `set-cookie`
- `pii`
- `email`
- `otp`

A sink MUST NOT emit the raw value or the sensitive key. The policy applies to structured
attributes and to any rendered representation derived from them. Redaction tests are required for
each new sink.

### Implemented behavior and explicit gap

**Current state:** Sink redaction is not enforced. `Slf4jOperationalEventSink` filters
`argument.*` keys only for placeholder rendering; that is not sensitive-data redaction. The current
sink formats named attributes and passes the original `cause` to SLF4J. Do not claim that the
current logger strips the terms above.

Until a follow-up implementation adds and tests redaction at the sink boundary, callers MUST avoid
putting sensitive values into event messages, arguments, attributes, or causes where the cause could
contain sensitive data. This caller discipline reduces risk but is not runtime enforcement.

### Safe example

The following is intentionally the complete event shape emitted by the example. The excluded key
families are not present in the event:

```kotlin
val event = OperationalEvent(
    name = "media.asset.preview.fallback",
    severity = Severity.INFO,
    message = "Using the local preview endpoint",
    attributes = mapOf(
        "assetId" to "asset-demo-001",
        "provider" to "local",
        "result" to "fallback",
    ),
)
```

The example excludes keys containing `token`, `password`, `secret`, `authorization`, `cookie`,
`set-cookie`, `pii`, `email`, and `otp`, and contains no values from those categories. A real
caller must make the same exclusion before constructing an event; the current sink cannot be relied
on to repair an unsafe event.

## Failures, causes, and correlation

### Failure and cause handling

**Implemented:** `OperationalEventPipelineBehavior` emits `bus.request.failed` with the original
`Throwable` in `cause`, then rethrows the same failure to its caller. The success path emits
`bus.request.completed`; the failed path remains observable as a failed request.

**Recommended:** Emit failures at the handler or mediator boundary where the request name and
operation duration are available. Preserve the original cause for infrastructure logging and
troubleshooting. Do not replace it with only `failure.message`, and do not catch and convert the
business failure into a success merely because event emission is best effort.

A sink may protect the request path from a sink-internal failure, as the current SLF4J sink does,
but that behavior must not suppress the handler failure or become a reason to omit the failure
event.

### Correlation status

**Current gap:** Centralized correlation propagation through Reactor `Context` and SLF4J MDC is not
implemented. `OperationalEvent` has no `correlationId` field, and non-MCP requests have no shared
correlation guarantee in this contract.

The MCP surface has separate correlation-specific behavior, including the `correlationId()` helper
used by publication tools and correlation values created by MCP error and audit infrastructure.
Those MCP-specific values must not be generalized into a platform-wide guarantee for ordinary SMP
requests.

**Recommended follow-up:** A future change should define the correlation contract, propagate it
through Reactor `Context` and SLF4J MDC, include a safe correlation value in the operational
representation, and add end-to-end coverage. That change must define trust, generation, logging,
response, and privacy rules before implementation. This guide does not implement or simulate it.

## Evolution and versioning

The current `OperationalEvent` contract has no explicit schema-version field. Compatibility is
therefore maintained through usage rules:

1. Keep event names stable and dotted.
2. Add optional attributes rather than removing or repurposing existing ones.
3. Keep attributes low-cardinality and safe for every sink.
4. Upgrade severity only when the operational meaning changes and the decision is recorded in an
   ADR.
5. Treat changes to `OperationalEvent`, `OperationalEventSink`, `Severity`, or `RequestOutcome` as
   shared-contract changes requiring an approved OpenSpec change and compatibility review.
6. If a breaking event shape or semantic rename is unavoidable, define the migration and ownership
   before changing consumers.

Do not add a version field, exporter, or compatibility shim solely because this guide mentions
versioning. Those are implementation decisions for a separately approved change.

## Test and verification matrix

### Implemented tests

| Boundary | Current evidence |
| --- | --- |
| Shared structured and legacy sink contract | [`OperationalEventSinkTest`](../shared/observability/src/test/kotlin/com/profiletailors/observability/OperationalEventSinkTest.kt) checks structured fields, cause identity, and legacy argument mapping |
| SMP request pipeline | [`OperationalEventPipelineBehaviorTest`](../server/smp/src/test/kotlin/com/profiletailors/smp/observability/infrastructure/OperationalEventPipelineBehaviorTest.kt) checks start/completion, failure rethrow, cause identity, and payload handling |
| Current SLF4J sink | [`Slf4jOperationalEventSinkTest`](../server/smp/src/test/kotlin/com/profiletailors/smp/observability/infrastructure/Slf4jOperationalEventSinkTest.kt) exercises every severity, rendering, nulls, and causes without throwing |
| No-op hook defaults | [`NoOpObservabilityHooksTest`](../server/smp/src/test/kotlin/com/profiletailors/smp/observability/infrastructure/NoOpObservabilityHooksTest.kt) checks the metrics and rate-limit no-op hooks |
| Existing application instrumentation | Media, publishing, identity, privacy, password-recovery, and waitlist observability tests cover their current call sites where applicable |

### Recommended follow-up tests

| Change | Required evidence |
| --- | --- |
| New sink or replacement sink | Unit tests prove every prohibited key family is absent from emitted output, including rendered messages and causes where applicable |
| New pipeline integration | Focused test through `OperationalEventPipelineBehavior` proves event names, outcome, duration, failure rethrow, and cause identity |
| New event producer | Unit test proves stable name, bounded attributes, safe values, and severity choice |
| Correlation implementation | End-to-end test proves generation or acceptance rules, Reactor `Context` propagation, MDC visibility, sink output, and response behavior |
| Shared contract change | Shared-module unit tests plus affected SMP consumers and compatibility evidence |

This matrix records implemented tests separately from recommended tests. It does not claim that the
recommended redaction or correlation tests exist today.

## Ownership and review checklist

Shared modules are governed by [ADR-0010: Shared Kernel Governance](./architecture/adr/0010-shared-kernel-governance.md).
Ownership belongs to the Principal Architect or an appointed cross-team platform group. SMP
observability bindings and consumers remain backend-owned and must preserve the shared module's
framework boundary.

Before approving an observability change, reviewers should confirm:

- The change is in the correct surface: shared Kotlin contract, SMP backend binding, or a separate
  web-owned contract.
- Current behavior and recommended behavior are labeled separately.
- The event name is stable, lowercase, dotted, and not value-bearing.
- Attributes are bounded, additive, opaque where identifiers are needed, and free of secrets and
  personal values.
- The original cause is preserved for failure events without changing the caller-visible failure.
- Handlers do not import SLF4J, Micrometer, OpenTelemetry, or an infrastructure sink directly.
- No new dependency crosses the `shared/` framework-free boundary.
- Redaction coverage exists for every new sink; the current `Slf4jOperationalEventSink` gap is not
  described as fixed.
- Correlation claims are limited to implemented MCP behavior until a platform-wide contract exists.
- Relevant unit, pipeline, integration, and end-to-end tests are present or explicitly recorded as
  deferred.
- SLA, SLO, and SLI ownership remains in the canonical contracts document.
- The relevant ADR, OpenSpec artifact, module catalog, and navigation link are updated when the
  contract or architecture changes.

## Relationship to SLA, SLO, and SLI documentation

This guide owns how application code uses the shared operational-event contract. It does not own
availability targets, latency targets, throughput limits, metric names, error budgets, or dashboard
operations.

The canonical owner for those claims is
[Observability Contracts & SLA Matrix](./observability-contracts.md). Use that document for
function-level SLAs and SLOs, SLI definitions, metric and tracing standards, and review cadence.
Use the [Prometheus & Grafana setup](./monitoring/prometheus-grafana-setup.md) and
[Actuator Security](./monitoring/actuator-security.md) for runtime monitoring configuration and
management-endpoint operations.

An operational event can provide evidence for investigation or a future metric adapter, but its
presence does not create an SLI and its `Severity` does not define an SLO. Changing an SLO or SLI
must update its owner document and any affected monitoring evidence rather than changing this guide
alone.

## Explicitly out of scope

This standard does not implement or approve:

- New operational sinks, Micrometer exporters, OpenTelemetry exporters, or replacement of
  `Slf4jOperationalEventSink`.
- Centralized correlation-id propagation through Reactor `Context` and SLF4J MDC.
- Frontend telemetry or error-reporting SDK integration.
- Changes to `OperationalEvent`, `OperationalEventSink`, `Severity`, or `RequestOutcome`.
- Runtime redaction enforcement in the current sink.
- A new event schema-version field or migration framework.

Those items require a separately scoped change with implementation, tests, ownership, and rollout
or rollback evidence. Documentation of the norm must not be read as evidence that any of those
capabilities is available.

## References

- [Shared module dependency catalog](./architecture/shared/dependencies.md)
- [ADR-0002: Adhere to Hexagonal Architecture](./architecture/adr/0002-adhere-to-hexagonal-architecture.md)
- [ADR-0010: Shared Kernel Governance](./architecture/adr/0010-shared-kernel-governance.md)
- [C4 architecture summary](./architecture/c4/SUMMARY.md)
- [C4 component model](./architecture/c4/03-component.md)
- [C4 code model](./architecture/c4/04-code.md)
- [Observability Contracts & SLA Matrix](./observability-contracts.md)
