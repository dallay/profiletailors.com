# ADR-0006: Resource Creation via POST

- Status: Accepted
- Date: 2026-06-21
- Decision owners: Principal Architect
- Scope: API Design
- Supersedes: None
- Superseded by: None

## Context
There was an earlier architectural consideration for `PUT`-based resource creation (with client-generated IDs) to support offline-first scenarios.

## Decision drivers
- Consistency (standard REST conventions).
- Security (server maintains control over identifier generation).
- Implementation speed (avoids complex idempotency logic on every creation endpoint).

## Decision
New resources MUST be created using `POST` endpoints where the server is responsible for generating the unique identifier.
Idempotency for creation SHOULD be handled via specific logic (e.g., uniqueness constraints on natural keys) rather than relying on client-supplied UUIDs in a `PUT` path.

## Scope and boundaries
- Applies to all public REST APIs.

## Alternatives considered
### PUT-based creation
- Description: `PUT /resources/{client-uuid}`
- Advantages: Native idempotency, offline-first support.
- Reason deferred: Offline support is currently a non-goal. The overhead of managing client-generated ID collisions and logic is not justified at this stage.

## Consequences
### Positive
- Predictable API behavior for consumers.
- Simplified backend logic for ID generation.
### Negative
- Requires a separate "Reserve/Draft" step for complex multi-part creations (e.g., Media uploads).
### Accepted trade-offs
- Lack of native offline support is accepted for current development simplicity.

## Compliance and enforcement
Enforced via API reviews.

## Verification
- Creation endpoints in Controllers use `@PostMapping`.

## Migration or remediation
None required.

## Revisit conditions
- The product requirements shift to prioritize true offline-first capability.
