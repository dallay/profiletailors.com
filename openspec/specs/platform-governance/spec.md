# Platform-Governance Specification — Observability Boundary Enforcement

> Change-scoped governance contract. No user-facing behavior. No new or modified `openspec/specs/` capabilities. Enforces ARCH-001 / ADR-0002 (hexagonal layer direction) and ADR-0010 (shared-kernel framework isolation) in `shared/*`.

## Purpose

Remove SLF4J and Jackson from `shared/*` domain/application code, route storage publish-failure signals through `OperationalEventSink`, and enforce the boundary with per-module blocking ArchUnit bans.

## Requirements

### Requirement: Per-module observability import bans

Each existing `*ArchTest` (storage, ratelimit) MUST ban SLF4J, Jackson, OTel, and Micrometer imports in `..domain..` and `..application..` packages. A new presentation test MUST provide equivalent coverage for `shared/presentation`. `..infrastructure..` owners (metrics, observability adapters, autoconfiguration, gateways/filters) MUST remain allowlisted. Bans MUST be blocking and demonstrated fail-then-pass against the known violations. Existing assertions MUST NOT be weakened.

#### Scenario: Ban fails on known violation then passes after migration

- GIVEN the bans enabled with the migration reverted
- WHEN the affected `*ArchTest` suite runs
- THEN it MUST fail identifying the offending domain/application import
- AND after migration it MUST pass with infrastructure owners still allowlisted

#### Scenario: Infrastructure logging remains permitted

- GIVEN a class in `..infrastructure..` importing SLF4J or Micrometer
- WHEN the module ArchTest runs
- THEN it MUST pass

### Requirement: Storage publish-failure events via sink

`StorageApplicationService` (3 sites) and `GeneratePresignedUrlUseCase` (1 site) MUST report event-publish failures via `OperationalEventSink.emit(WARN, <dotted-name>, ...)` with a defaulted `NoOpOperationalEventSink` injection. Events MUST carry `operation`, `provider`, and sanitized `bucket` only; `key` and payloads MUST NEVER be emitted. Swallow-and-continue MUST be preserved and `CancellationException` MUST be rethrown. Captured-event tests MUST assert name, severity, attributes, and absence of `key`.

#### Scenario: Publish failure emits key-safe event and continues

- GIVEN an event-publisher failure during upload, download, delete, or presigned-URL flow
- WHEN the operation completes
- THEN a WARN event with a stable dotted name and only allowlisted attributes MUST be emitted
- AND the operation MUST swallow-and-continue

#### Scenario: Cancellation is never swallowed

- GIVEN a `CancellationException` in the publish path
- WHEN the failure handler runs
- THEN it MUST rethrow and MUST NOT emit a failure event for it

### Requirement: RHSFilterParser reclassified out of domain

`RHSFilterParser` MUST live outside any `..domain..` package so Jackson leaves domain-packaged code. The factory import and all test imports MUST be updated. The parser MUST NOT log or emit the full query map or payload.

#### Scenario: Parser resolves outside domain

- GIVEN the moved parser with updated factory and test imports
- WHEN the presentation and spring-boot-common suites run
- THEN they MUST pass with no `..domain..` class importing Jackson or SLF4J
- AND no log or event output contains the full query map

### Requirement: Docs synchronized

`docs/architecture/shared/dependencies.md`, `docs/observability-usage.md`, and `docs/observability-contracts.md` MUST document the new storage-to-observability edge and the canonical event names and attributes.

#### Scenario: Docs match implementation

- GIVEN the implemented events and module edge
- WHEN the three docs are inspected
- THEN names, attributes, and the dependency edge MUST match the code

## Non-Requirements

- Sink redaction hardening — follow-up.
- Legacy `info`/`warn`/`error` deprecation — follow-up.
- Common aggregator module or `just architecture-check` — excluded per governance policy.
- Metric and SLO renames — Prometheus contracts unchanged.

## Assumptions

- `strict_tdd` drift unresolved; `openspec/config.yaml` untouched.
- `chain_strategy` undecided — tasks-phase input.
