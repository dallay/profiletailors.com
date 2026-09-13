# ADR-0021: Shared Operational Event Safety Boundary

- Status: Accepted
- Date: 2026-09-10
- Decision owners: Principal Architect
- Scope: `shared/observability/` and SMP operational-event adapters and producers
- Supersedes: None
- Superseded by: None

## Context

Operational events are emitted from framework-free application code and adapted to SLF4J by SMP infrastructure. Safety behavior must be consistent across adapters, and operational telemetry must not change business outcomes. Legacy severity helpers also allowed unstable messages and positional arguments to spread through production code.

## Decision

`BestEffortOperationalEventSink` is the single safety boundary. It sanitizes structured events, converts throwable details to a safe class name, removes the original cause before adapter emission, swallows ordinary `Exception` failures, and preserves `CancellationException` and fatal `Error` propagation. The SLF4J adapter only formats and emits already-sanitized events.

Production code emits stable dotted event names with bounded named attributes. Deprecated severity helpers and `emitLegacy` are removed after a production-source zero-consumer scan. Sink failures do not produce recursive self-diagnostic events because re-entry can recurse and a fallback channel would add an unapproved dependency.

Sensitive-key matching is case-insensitive and protects credential, token, password, secret, authorization, authentication, auth-token, auth-header, API-key, cookie, set-cookie, OTP, email, and PII families. Ordinary fields such as `author`, `authorId`, and `authorship` remain available for operational context.

## Consequences

All adapters receive the same sanitized contract and ordinary telemetry failures remain isolated from business operations. Producers must choose stable event names and bounded attributes, and sensitive values must not be included in messages or attributes. New adapters must be wrapped by the shared best-effort boundary.

Correlation or request-context propagation, distributed tracing, exporters, and a real OpenTelemetry adapter are not implemented by this decision. Those require a separate design and implementation change.

## Explicit Non-goals

Correlation or request-context propagation, distributed tracing, exporters, and a real OpenTelemetry adapter are not implemented by this decision. They require a future approved contract and architecture change.
