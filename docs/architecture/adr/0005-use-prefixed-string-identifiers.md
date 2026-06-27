# ADR-0005: Use Prefixed String Identifiers

- Status: Accepted
- Date: 2026-06-21
- Decision owners: Principal Architect
- Scope: Repository-wide
- Supersedes: None
- Superseded by: None

## Context
Standard UUIDs (e.g., `550e8400-e29b-41d4-a716-446655440000`) are difficult to distinguish in logs, URLs, and debugging sessions.

## Decision drivers
- Observability (immediately know what entity an ID refers to).
- Developer experience (easier log searching and communication).
- Uniqueness (guaranteed by backing UUID).

## Decision
Entity identifiers MUST use a human-readable prefix followed by a UUID.
- Format: `{prefix}-{uuid}`
- Database Type: `varchar(64)` (or similar).

Standard Prefixes:
- User: `user-`
- Workspace: `ws-`
- Membership: `wm-`
- Publication: `pub-`
- Asset: `pa-`

Exception: Purely internal or infrastructure-only records (e.g., `secure_credentials`) MAY use raw UUIDs if they never appear in logs or public APIs.

## Scope and boundaries
- Applies to all Aggregate Roots and public-facing entities.

## Alternatives considered
### Raw UUIDs
- Advantages: Native database support, less storage space.
- Disadvantages: Poor observability.
- Reason rejected: Prefixed IDs provide significant value during troubleshooting.

## Consequences
### Positive
- Logs like "Failed to load ws-abc123" are self-documenting.
- Prevents accidental usage of a User ID where a Workspace ID is expected.

### Negative
- Slightly more storage and indexing overhead in PostgreSQL.
- Requires string manipulation/concatenation in the backend.

## Compliance and enforcement
Enforced during code review and in Domain models.

## Verification
- Check primary key types in Liquibase migrations.
- Check ID generation logic in infrastructure adapters.

## Migration or remediation
Codebase already largely follows this pattern.

## Revisit conditions
- Storage or indexing performance becomes a bottleneck due to large string IDs.
