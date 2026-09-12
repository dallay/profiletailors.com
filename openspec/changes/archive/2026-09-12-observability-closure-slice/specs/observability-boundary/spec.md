# Technical Specification: Observability Boundary Closure

## Applicability

This is a technical architecture/refactoring specification, not a product capability delta. The
proposal declares no product capability changes, and OpenSpec policy prohibits standalone
capability specs for purely technical refactors. This artifact is therefore kept under the active
change folder as implementation-contract and verification guidance; it MUST NOT be promoted to
`openspec/specs/`.

## Requirements

### Requirement: Single-owner failure isolation

`BestEffortOperationalEventSink` MUST be the sole boundary that sanitizes events and isolates
ordinary sink failures. `Slf4jOperationalEventSink` MUST only format and emit already-sanitized
events; it MUST NOT duplicate sanitization or swallow ordinary failures. Failure isolation MUST NOT
alter business operation outcomes.

#### Scenario: Ordinary emission failure is isolated once

- GIVEN the configured adapter throws an ordinary `Exception`
- WHEN an operational event is emitted through the best-effort sink
- THEN emission returns without throwing
- AND the business operation may continue
- AND the adapter contains no independent swallow path

### Requirement: Cancellation and fatal Error propagation

The boundary MUST rethrow `CancellationException` without attempting another emission. It MUST NOT
swallow fatal `Error` instances. A business failure recorded as an event cause MUST remain the
original `Throwable`, while event-publication failure MUST NOT convert a business failure into
success.

#### Scenario: Cancellation is preserved

- GIVEN event emission or sanitization encounters a `CancellationException`
- WHEN the best-effort sink handles the event
- THEN the same cancellation propagates
- AND no fallback emission is attempted

#### Scenario: Fatal errors are preserved

- GIVEN the adapter throws an `Error`
- WHEN the event is emitted through the boundary
- THEN the `Error` propagates to the caller
- AND it is not converted into a successful emission

### Requirement: Safe, non-mutating sanitization

Sanitization MUST return a safe event without mutating the source event or its attribute map. It
MUST recursively protect nested sensitive attributes, case-insensitively, while preserving safe
operational context. Sensitive matching MUST include `authToken`, `authorization`, `authHeader`,
`credential`, `token`, `password`, and `secret`. Ordinary keys such as `author`, `authorId`, and
`authorship` MUST remain visible.

#### Scenario: Sensitive and ordinary keys are distinguished

- GIVEN an event contains nested keys from both the sensitive and ordinary key sets
- WHEN the event is sanitized
- THEN each sensitive value is redacted
- AND `author`, `authorId`, and `authorship` retain their values
- AND the original event and nested maps remain unchanged

### Requirement: Complete structured-event migration

Every production consumer MUST emit named structured events rather than deprecated severity
helpers. Deprecated helpers and `emitLegacy` MUST be deleted only after a repository-wide production
scan proves zero consumers; tests MAY retain focused compatibility coverage only while migration is
incomplete.

#### Scenario: Migration is complete before helper deletion

- GIVEN all identified media, identity, privacy, and other production callers are migrated
- WHEN the production source scan runs before deletion
- THEN no deprecated helper import or call remains
- AND structured event names, causes, and bounded attributes are asserted by focused tests
- AND only then are the helpers and `emitLegacy` removed

### Requirement: Documentation and source-of-truth reconciliation

The final observability ownership, sanitization boundary, structured-only producer rule, migration
status, test evidence expectations, and non-goals MUST be reconciled in the owning observability
usage and contracts documentation. Any affected durable architecture reference MUST be updated only
when its claim changes. Documentation MUST distinguish implemented behavior from recommendations.

#### Scenario: Documentation matches the final contract

- GIVEN the implementation and focused tests establish the final boundary
- WHEN the observability documentation is reviewed
- THEN ownership and safety claims match the implementation
- AND deprecated migration claims are no longer stale
- AND links and source references resolve

### Requirement: Explicit non-goals

This closure slice MUST NOT introduce correlation or request-context propagation, distributed
tracing, exporters, or a real OpenTelemetry adapter. It MUST NOT change business exception
propagation, handler outcomes, or unrelated SLF4J logging.

#### Scenario: Non-goals remain absent

- GIVEN the closure slice is implemented and reviewed
- WHEN the changed-source and documentation scope is inspected
- THEN no correlation, tracing, exporter, or OpenTelemetry adapter behavior is added
- AND business outcomes and unrelated logging behavior remain unchanged
