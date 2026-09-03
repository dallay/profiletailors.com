# Invitations Specification

## Purpose

`Invitation` is the canonical standalone authorization to register. It owns identity, source,
target, semantic lifecycle, and acceptance facts—not waitlist entries, provisioning, notifications,
or bearer-token handoff. DALLAY-568 owns admin commands; DALLAY-570 conversion; DALLAY-567
provisioning; DALLAY-565 notifications; DALLAY-566 concrete token generation, hashing, lookup,
enforcement, and handoff. DALLAY-564 MUST NOT invent those implementations, endpoints, or an
additional aggregate.

## Requirements

### Requirement: DDD markers and identity

`Invitation` MUST be `@AggregateRoot`. `InvitationId`, `InvitationStatus`, and `InvitationSource`
MUST be immutable `@ValueObject` types. `InvitationId` remains UUID-backed and persists as raw
PostgreSQL `uuid`; aggregate references MUST be identities only.

#### Scenario: Marker coverage

- GIVEN platform-admin production types
- WHEN DDD tests inspect invitation types
- THEN markers, immutability, and UUID identity MUST be verified

### Requirement: Construction and source invariants

Construction MUST reject blank workspace/issuer identities, non-normalized trimmed-lowercase target
email, blank opaque token material, and `expiresAt <= createdAt`. `DIRECT` MUST have no reference;
`WAITLIST` MUST have a nonblank logical waitlist-entry reference.

#### Scenario: Invalid invitation fails

- GIVEN an invalid field or source/reference combination
- WHEN an invitation is constructed
- THEN construction MUST fail before persistence

### Requirement: Semantic lifecycle

Statuses MUST be exactly `ACTIVE`, `ACCEPTED`, `EXPIRED`, and `REVOKED`. Only `ACTIVE` MAY transition
to another state; terminal states MUST reject mutation. Status MUST NOT contain delivery fields.

#### Scenario: Delivery is independent

- GIVEN an active invitation and notification failure
- WHEN Notifications records that failure
- THEN invitation status and semantic fields MUST remain unchanged

### Requirement: Explicit expiration

`expiresAt` is exclusive: `ACTIVE` is unusable at or after the boundary. Reading an expired active
row MUST NOT write persistence. Only explicit `expire(at)` MAY materialize `EXPIRED`, at or after
the boundary; scheduling and cleanup are outside scope.

#### Scenario: Expired read does not mutate

- GIVEN an active row where `now >= expiresAt`
- WHEN it is read without `expire(at)`
- THEN it remains stored as `ACTIVE` but MUST be unusable

### Requirement: Acceptance metadata

`ACCEPTED` MUST contain both `acceptedAt` and `acceptedPrincipalId`; every other status MUST contain
neither. `accept(at, principal)` MUST preserve all other invitation facts.

#### Scenario: Acceptance records one principal

- GIVEN an active, unexpired invitation
- WHEN principal `P` accepts at `at`
- THEN exactly `at` and `P` MUST be recorded

### Requirement: Canonical repository transitions

A framework-free `InvitationRepository` MUST provide aggregate reads/writes and conditional lifecycle
transitions. Adapters MUST map the `invitations` schema and report success only when the expected
current state changed; handlers MUST use the port.

#### Scenario: Stale transition is rejected

- GIVEN a transition expects `ACTIVE` but storage is `REVOKED`
- WHEN the conditional operation runs
- THEN no row MUST change and conflict/unavailable MUST be reported

### Requirement: One-time concurrent acceptance

The acceptance contract MUST atomically permit at most one `ACTIVE`→`ACCEPTED` transition per
invitation. Under contention exactly one caller MAY succeed; provisioning remains DALLAY-567.

#### Scenario: Concurrent clients contend

- GIVEN two clients accept one active invitation concurrently
- WHEN both transactions finish
- THEN exactly one succeeds and one accepted state persists

### Requirement: Schema protections

The schema MUST enforce UUID identity, required fields, source/reference consistency, normalized
email, `expires_at > created_at`, accepted-metadata consistency, unique opaque lookup/token material,
and at most one active invitation per workspace and normalized target email. It MUST NOT persist raw
tokens or add delivery columns.

#### Scenario: Impossible row is rejected

- GIVEN partial acceptance metadata or invalid source/reference
- WHEN PostgreSQL receives the write
- THEN the write MUST be rejected

### Requirement: Safe audit and observability

Lifecycle evidence MUST use low-cardinality invitation ID, status, outcome, timestamps, and correlation
data. Raw tokens, token-bearing URLs, and full target emails MUST NOT cross invitation, audit, log,
or metric boundaries; downstream owners publish their events.

#### Scenario: Evidence contains no bearer

- GIVEN a lifecycle transition succeeds
- WHEN audit or metrics evidence is emitted
- THEN it MUST identify the invitation without bearer or full-email values

### Requirement: Token ownership

Invitation MAY persist only non-reversible token material and opaque lookup data required by DALLAY-566.
It MUST NOT define token algorithms, raw-token handoff, URL construction, or delivery behavior.

#### Scenario: Notification failure stays external

- GIVEN DALLAY-565 cannot deliver
- WHEN its failure is recorded
- THEN no Invitation field or semantic status MUST change

### Requirement: Legacy compatibility

`WaitlistInvitation`, `waitlist_invitations`, legacy commands, queries, history, and delivery bridge
MUST remain usable until DALLAY-565/570 define migration. DALLAY-564 MUST NOT drop, rename,
backfill, or substitute those flows.

#### Scenario: Legacy flow remains separate

- GIVEN an existing legacy waitlist invitation
- WHEN its operator flow reads or updates it
- THEN it MUST use its own aggregate and delivery fields

### Requirement: Documentation and strict TDD

Implementation MUST document UUID, explicit expiry materialization, ownership, and compatibility in
affected architecture, data-model, operational, and OpenSpec documents. Strict TDD MUST add failing
domain/marker tests before production changes, then port, schema, and PostgreSQL contention tests.
This specification authorizes no production implementation.

#### Scenario: Contract evidence is reviewable

- GIVEN the change is reviewed
- WHEN artifacts and test history are inspected
- THEN boundaries and the red/green sequence MUST be verifiable
