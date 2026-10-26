# ADR-0029: Use Raw UUID Identifiers for Internal Entities

- Status: Accepted
- Date: 2026-10-25
- Decision owners: Principal Architect
- Scope: Repository-wide internal entity identifiers
- Supersedes: ADR-0005
- Superseded by: None

## Context

The system uses prefixed UUID strings for some internal entity identifiers and raw UUID strings for others. The mixed representation causes parsing failures across internal boundaries, increases string conversion, and prevents PostgreSQL UUID typing. Provider identifiers, opaque storage keys, secrets, and idempotency keys have different semantics and are not entity identifiers.

## Decision drivers

- One canonical representation for internal entity identity across Kotlin and API v1.
- Native UUID persistence and referential integrity in PostgreSQL.
- Explicit nominal Kotlin types to distinguish identifier concepts.
- Preserve external and opaque identifier formats.
- Maintain structured observability without encoding type information into identifier values.

## Decision

Internal entity identifiers MUST use UUID values in Kotlin and canonical unprefixed UUID strings at API v1 and wire boundaries. PostgreSQL columns for inventoried internal identifiers and their relations MUST use the UUID type. Existing records MUST be converted through a separately reviewed production migration plan before UUID-only deployment.

This decision does not authorize automatic Liquibase data conversion or production SQL execution. Provider IDs, credentials, secrets, idempotency keys, storage keys, and other opaque identifiers MUST retain their own format. API v1 media type remains `application/vnd.api.v1+json`.

## Alternatives considered

### Prefixed strings

Rejected because prefixes duplicate type information, require parsing and string manipulation, and create inconsistent representations between existing internal flows.

### Context-only field names in logs

Accepted as the observability approach. Structured fields identify the identifier kind and context without changing the identifier value.

## Consequences

Kotlin identifier types must retain nominal distinctions. Generators, parsers, persistence mappings, API clients, event consumers, fixtures, and schema definitions must be migrated from the confirmed inventory. Deployment is blocked until the user executes the reviewed SQL and validates production preconditions and postconditions. No downtime, rolling compatibility window, or rollout mode is assumed by this ADR.

## Compliance and verification

The [archived change](../../../.agents/sdd/changes/archive/2026-10-26-uuid-native-identifiers/archive-report.md) retains the inventory, SQL plan, rollout evidence, implementation tasks, verification and QA. PostgreSQL relation tests and shortlinks regression tests validate the internal UUID contract with the limitations preserved in both reports. Production SQL remains unexecuted; its execution and validation belong to the user under separate operational approval, not this implementation.
