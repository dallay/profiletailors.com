# Consent Scopes Specification

## Purpose

Define consent ownership for tenant workspaces and public waitlists without fabricating tenants.

## Requirements

### Requirement: Exclusive Real-Entity Consent Scope

Consent MUST belong to exactly one `WORKSPACE` or `WAITLIST` scope, identified by an existing
entity's UUID. A `WAITLIST` scope MUST NOT create or simulate a tenant. The semantic waitlist key
MUST remain a semantic key; it MUST NOT replace a workspace UUID reference or serve as an inferred
numeric identity.

#### Scenario: Public capture

- GIVEN an existing waitlist
- WHEN public capture records consent
- THEN the scope MUST be `WAITLIST` with the real list UUID and no workspace reference
- AND capture MUST NOT fabricate a tenant

### Requirement: Scoped Active Uniqueness

Active uniqueness MUST include scope, subject, purpose and policy version. Different lists and
workspaces MUST remain isolated even when they share subject and purpose.

#### Scenario: Idempotency

- GIVEN active consent exists in a scope
- WHEN equivalent consent is recorded again in the same scope
- THEN the active projection MUST NOT be duplicated

#### Scenario: Isolation

- GIVEN two lists and a workspace share a subject and purpose
- WHEN each scope records consent
- THEN their consent state MUST remain independent

#### Scenario: Concurrency

- GIVEN two equivalent consent recordings target the same scope concurrently
- WHEN both recordings complete
- THEN exactly one active projection MUST exist

### Requirement: Scope-Restricted Withdrawal and History

Withdrawal MUST preserve history and events and MUST affect only the requested scope.

#### Scenario: Withdrawal

- GIVEN consent exists in multiple scopes
- WHEN consent is withdrawn in one scope
- THEN the other scopes MUST remain unchanged
- AND events and history for the withdrawn scope MUST be preserved

### Requirement: Lossless Historical Scope Migration

Migration MUST resolve historical `waitlist:<key>` values to the corresponding existing list and
convert projections and events without losing rows, subject identity, purpose, version or legal
evidence. A missing or ambiguous historical reference MUST abort with rollback. Conversion MUST NOT
discard consent or fabricate a workspace or list.

#### Scenario: Valid history

- GIVEN historical projections and events refer to a resolvable `waitlist:<key>`
- WHEN the UUID cutover converts them
- THEN their relationships, identities and legal evidence MUST be preserved

#### Scenario: Invalid history

- GIVEN a historical list is missing or its semantic key is ambiguous
- WHEN migration attempts to resolve the reference
- THEN migration MUST abort with rollback and no persisted mutation

### Requirement: Tenant Endpoint Scope Security

Existing tenant endpoints MUST retain authorization and `WORKSPACE` scope. They MUST NOT permit
access to `WAITLIST` records because a workspace reference is null.

#### Scenario: Tenant security

- GIVEN public `WAITLIST` consent exists
- WHEN a user queries or withdraws workspace consent through tenant endpoints
- THEN the endpoints MUST NOT return or modify public consent records
