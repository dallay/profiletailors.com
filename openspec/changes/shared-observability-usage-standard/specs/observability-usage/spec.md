# Observability Usage Standard Specification

## Purpose

Document the implemented observability surface and the recommended usage norm without changing
runtime code or claiming deferred behavior exists.

## Requirements

### Requirement: Shared observability contract

Implemented: `OperationalEvent`, `OperationalEventSink`, `Severity`, `NoOpOperationalEventSink`, extensions, and
`RequestOutcome` are the documented shared contracts. Consumers SHOULD use the structured event
contract and MUST NOT expand this public surface without an approved change.

#### Scenario: Contract inventory is reviewable

- GIVEN the shared modules are inspected
- WHEN the standard is checked against their sources
- THEN each listed type and extension is either present or explicitly marked as proposed

### Requirement: Layered backend usage

Implemented usage is mixed. Recommended: domain code and application handlers MUST depend on an
observability port; infrastructure MUST bind that port to sinks. Handlers MUST NOT directly use
SLF4J, Micrometer, or OpenTelemetry.

#### Scenario: Handler boundary remains framework-independent

- GIVEN a domain or application handler emits an operational event
- WHEN its dependencies are inspected
- THEN it uses a port and contains no direct logging or exporter dependency

### Requirement: Kotlin-only web boundary

Implemented: no web consumer is documented. The norm is that `shared/observability` MUST remain
Kotlin-only; `apps/web/**` and `shared/web/**` MUST NOT import it.

#### Scenario: Web boundary is preserved

- GIVEN web and shared-web source trees are scanned
- WHEN imports are checked
- THEN no source imports `com.profiletailors.observability`

### Requirement: Redaction and sensitive data

Implemented: sink redaction is not enforced. Recommended: every sink MUST remove keys containing
`token`, `password`, `secret`, `authorization`, `cookie`, `set-cookie`, `pii`, `email`, or `otp`
before emission, and MUST NOT emit raw secret or personal values.

#### Scenario: Sensitive attributes are excluded

- GIVEN an event contains a secret-like attribute
- WHEN a sink emits it
- THEN the emitted representation contains neither that value nor its sensitive key

### Requirement: Errors and causes

Implemented: the pipeline emits started, completed, and failed request events; failed events carry
the original `cause`. Recommended: failures MUST be emitted at the handler boundary and preserve the
cause while the request failure remains observable to its caller.

#### Scenario: Failed request preserves outcome and cause

- GIVEN a handler throws a failure
- WHEN the pipeline handles the request
- THEN a failed event contains the same cause and the failure is rethrown

### Requirement: Correlation is deferred

Implemented: centralized correlation propagation does not exist; `correlationId()` is limited to
MCP tools. Reactor `Context` plus SLF4J MDC propagation is a deferred follow-up, not a current guarantee.

#### Scenario: No false correlation guarantee

- GIVEN this standard is used to assess a non-MCP request
- WHEN correlation support is evaluated
- THEN the result is recorded as a gap/deferred item, not as implemented behavior

### Requirement: Evolution of event metadata

Recommended: event `name` values MUST remain stable and dotted; attributes SHOULD be additive and
low-cardinality; severity upgrades MUST be justified by an ADR. No existing API is modified here.

#### Scenario: Compatible metadata evolution

- GIVEN an existing event is extended
- WHEN its metadata is reviewed
- THEN its name remains stable, new attributes are additive, and any severity upgrade cites an ADR

### Requirement: Verification matrix

Implemented tests cover sink severity/rendering, pipeline success/failure, and existing observability
call sites. Recommended: new sinks MUST have redaction unit tests; pipeline integrations MUST test
`OperationalEventPipelineBehavior`; correlation requires an end-to-end test when implemented.

#### Scenario: Verification scope is explicit

- GIVEN the standard is reviewed before implementation
- WHEN test evidence is listed
- THEN implemented tests are separated from recommended follow-up tests

### Requirement: Canonical documentation

`docs/README.md` MUST point readers to this standard. `docs/observability-contracts.md` remains the
canonical SLO/SLI contract, while monitoring documents remain runtime-operation references; neither
may be presented as this usage standard.

#### Scenario: Documentation ownership is unambiguous

- GIVEN a reader starts at `docs/README.md`
- WHEN observability guidance is sought
- THEN the index links to this change and distinguishes usage, SLO/SLI, and monitoring documents
