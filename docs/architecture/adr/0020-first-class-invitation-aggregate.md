# ADR-0020: Model Invitation as a First-Class Domain Capability

## Status

Accepted — 2026-09-02

## Context

The `Invitation` aggregate inside `platformadmin.domain` existed but was partial: the table was
already there (`invitations`), but DDD markers, lifecycle invariants, a framework-free repository
contract, R2DBC persistence, Liquibase protections, version CAS, and concurrency proof were all
incomplete or absent. Existing invitation flows routed through `WaitlistInvitation`, coupling
authorization to register with waitlist lifecycle and email delivery state.

Direct invitations and admin-driven issuance could not be modeled without inventing fake waitlist
entries, and future sources of invitations would compound the coupling. The acceptance path also
persisted a token-bearing durable value that violated the DALLAY-566 token boundary.

The repository needed to:

1. Make `Invitation` a standalone aggregate independent from `WaitlistInvitation`.
2. Add additive Liquibase protections for source/reference, normalized email, expiry, acceptance
   metadata, status, version, uniqueness, and lookup indexes without touching waitlist tables.
3. Introduce a canonical R2DBC adapter with optimistic version CAS for exactly-once acceptance.
4. Prove concurrency safety with a two-client simultaneous acceptance race.
5. Keep the token boundary opaque: no raw token, no accept URL, no delivery field on the aggregate;
   no second token generator, hasher, or URL builder.

## Decision

Canonicalize the existing `platformadmin.domain.Invitation` as the semantic authorization aggregate.

### Aggregate boundary

`Invitation` is marked `@AggregateRoot`. `InvitationId`, `InvitationStatus`, and `InvitationSource`
are immutable `@ValueObject` types. `WaitlistInvitation` remains a separate legacy aggregate with
its own table, repository, handlers, and audit events. The two never share storage or commands.

### Identifier

`InvitationId` stays `@JvmInline value class InvitationId(UUID)` and persists as PostgreSQL `uuid`.
This is a scoped exception to ADR-0005 (prefixed string identifiers). No prefixed-ID migration is
performed because the table, acceptance path, and tests already assume UUID identity and converting
would not improve the token boundary.

### Lifecycle

`ACTIVE` may transition only to `ACCEPTED`, `EXPIRED`, or `REVOKED`. Terminal states reject mutation.
`accept(at, principalId)` requires `at < expiresAt` and records the principal plus an incremented
version. `expire(at)` materializes `EXPIRED` when `at >= expiresAt`. `revoke()` materializes
`REVOKED`. `version` is monotonically incremented on every transition.

### Token ownership

The aggregate stores only `tokenHash` (non-reversible HMAC). The repository accepts an opaque
candidate key string that maps to a lookup column (`candidate_key`). Raw tokens, accept URLs, and
delivery fields are forbidden on the aggregate and the repository contract. DALLAY-566 owns the
token subsystem; DALLAY-565 owns notifications; this change does not introduce either.

### Persistence and CAS

A single canonical R2DBC adapter `R2dbcInvitationRepository` owns the `invitations` table. It
exposes `findById`, `findByCandidateKeyForUpdate` (locked read), `save`, and
`updateIfVersionMatches` (version CAS). The SQL `UPDATE` sets the new version and matches the
predecessor version (`WHERE version = expectedVersion = new_version - 1`). Stale attempts return
zero rows and the transition is rejected.

### Acceptance facade

`AcceptInvitationHandler` resolves the candidate key, looks up the locked invitation, verifies the
authenticated principal matches the invited email, ensures `isActive(now)`, reconciles workspace
membership through `WorkspaceMembershipProvisioner`, and calls `markAccepted` only after the
membership is provisioned. `markAccepted` uses the acceptance facade which delegates to
`updateIfVersionMatches` for the CAS. A failure to mark accepted throws
`InvitationNotAcceptableException` and leaves no partial state.

### Schema protections

`db/changelog/platform-admin/005-harden-invitations.yaml` adds:

- `version bigint NOT NULL DEFAULT 0`.
- Check constraints for source/reference rules, normalized email, expiry ordering, and acceptance
  metadata alignment with status.
- A partial unique index `(workspace_id, invited_email_normalized) WHERE status = 'ACTIVE'`.
- Supporting lookup indexes on `candidate_key`, `token_hash`, `(workspace_id, status)`.

No waitlist table is modified.

## Consequences

Positive:

- Direct and future invitation sources are independent of waitlist lifecycle and delivery state.
- Exactly-once acceptance is enforced at the application and persistence boundary.
- The token boundary is opaque; DALLAY-566 can take over without changing this aggregate.
- DDD marker coverage tests verify the boundary stays stable.
- Concurrency proof is recorded in `R2dbcInvitationRepositoryTest.concurrent acceptance clients
  allow one success and one membership`.

Negative:

- Two aggregates (`Invitation` and `WaitlistInvitation`) coexist and must remain separated.
- Existing acceptance paths still route through the application facade; downstream features
  (DALLAY-565/566/567/568/570) must adopt the canonical aggregate before the legacy path is
  retired.
- UUID identity is a scoped exception to ADR-0005; future aggregates must still use prefixed
  string identifiers.

## References

- `openspec/changes/dallay-564-first-class-invitation/{proposal.md,design.md,specs/invitations/spec.md}`
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/Invitation.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/contracts/InvitationRepository.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/persistence/R2dbcInvitationRepository.kt`
- `server/smp/src/main/resources/db/changelog/platform-admin/005-harden-invitations.yaml`
- `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/infrastructure/persistence/R2dbcInvitationRepositoryTest.kt`
