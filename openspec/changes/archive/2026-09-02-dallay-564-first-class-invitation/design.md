# Design: First-Class Invitation

## Technical Approach

Canonicalize the existing `platformadmin.domain.Invitation` as the semantic authorization aggregate,
without replacing `WaitlistInvitation`. Keep invariants and immutable transitions in pure Kotlin;
expose one framework-free repository contract from `platformadmin.application`; map the existing
`invitations` table through R2DBC. Reuse the acceptance transaction seam so DALLAY-567 can provision
membership and DALLAY-566 can consume invitations without a second aggregate or token subsystem.
DALLAY-568/570/565 remain owners of commands, conversion, delivery, and notification state.

## Architecture Decisions

| Decision | Choice | Alternatives and rationale |
|---|---|---|
| Aggregate boundary | Mark `Invitation` with `@AggregateRoot`; mark `InvitationId`, `InvitationSource`, and `InvitationStatus` with `@ValueObject`. Keep `Invitation` immutable and responsible for `accept`, `expire`, and `revoke` invariants. | Do not mark delivery types or fold `WaitlistInvitation` into this aggregate; that would couple semantic validity to transport or waitlist lifecycle. |
| Identifier | Retain `@JvmInline InvitationId(UUID)` and PostgreSQL `uuid`. Record a scoped exception to ADR-0005; no prefixed-ID migration. | Converting to a prefixed string would break the already-landed table and acceptance path without improving the token boundary. |
| Lifecycle | `ACTIVE` may transition only to `ACCEPTED`, `EXPIRED`, or `REVOKED`; terminal states reject mutation. `accept(at, principalId)` requires `at < expiresAt`; `expire(at)` requires `at >= expiresAt` and materializes `EXPIRED`; non-accepted states require null acceptance metadata. | Computed-only expiry leaves stale active rows and makes later reads disagree with persisted state. A scheduler is deferred; access/expiry commands materialize the state. |
| Token ownership | Keep only stored non-reversible `tokenHash` and opaque candidate lookup data. No raw token, accept URL, or delivery field crosses the Invitation, audit, metric, or durable-event boundary. | Generation, hashing, candidate derivation, recipient binding, and ephemeral handoff belong exclusively to DALLAY-566/565. |

## Data Flow

```text
DALLAY-568/570 producer -> InvitationRepository -> invitations (semantic state)
DALLAY-566 candidate key -> row lock -> transition -> DALLAY-567 membership transaction
                                      \-> safe audit + low-cardinality metrics
DALLAY-565 consumes InvitationId and keeps delivery state in Notifications
```

The canonical `InvitationRepository` lives in `platformadmin.application.contracts` and exposes
`findById`, `save`, candidate-key lookup, and conditional lifecycle transitions. Its candidate key is
opaque and supplied by DALLAY-566; the repository never derives or returns bearer values.
`R2dbcInvitationRepository` owns SQL, row mapping, UUID conversion, and optimistic version handling.
The existing `InvitationAcceptanceRepository` becomes a temporary façade over this adapter, not a
second SQL or semantic repository.

All multi-statement acceptance/provisioning work uses `AtomicTransactionRunner`. Candidate lookup is
`SELECT ... FOR UPDATE`; acceptance, expiry, and revocation use conditional updates (`status =
'ACTIVE'`, `expires_at >/< :at`, expected `version`) and increment `version`. A lost update is a
deterministic transition failure. PostgreSQL uniqueness plus the transaction boundary proves exactly
one acceptance and one membership under contention.

## File Changes

| File | Action | Description |
|---|---|---|
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/{Invitation.kt,InvitationId.kt}` | Modify | Add markers, explicit transitions, metadata invariants, and version. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/{AcceptInvitation.kt,contracts/InvitationRepository.kt}` | Modify/Create | Use the canonical port and preserve the safe acceptance result. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/persistence/R2dbcInvitationRepository.kt` | Create/replace | Implement row mapping, locked lookup, CAS transitions, and safe output. |
| `server/smp/src/main/resources/db/changelog/platform-admin/005-harden-invitations.yaml` and `db.changelog-master.yaml` | Create/Modify | Add version, lifecycle/source/email checks, active `(workspace_id, invited_email_normalized)` uniqueness, and indexes. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/{PlatformAdminMarkerCoverageTest.kt,domain/InvitationTest.kt,integration/*Invitation*Test.kt}` | Modify/Create | Cover markers, invariants, transitions, schema, rollback, scope, and races. |
| `AdminAuditEvent.kt`, invitation observability adapter/tests | Modify/Create | Add safe actions and bounded transition metric tags. |
| `docs/architecture/adr/0005-use-prefixed-string-identifiers.md`, `docs/architecture/c4/04-code.md`, `docs/architecture/data-model/README.md`, `docs/architecture/transaction-policy.md`, `docs/observability-contracts.md`, `docs/infrastructure/private-beta-correlation-matrix.md` | Modify | Record UUID, expiry, canonical table, pivots, and redaction. |

## Interfaces / Contracts

```kotlin
interface InvitationRepository {
    suspend fun findById(id: InvitationId): Invitation?
    suspend fun findByCandidateKeyForUpdate(candidateKey: String): Invitation?
    suspend fun save(invitation: Invitation, candidateKey: String): Invitation
    suspend fun updateIfVersionMatches(invitation: Invitation): Boolean
}
```

The port treats candidate keys as opaque; it does not generate, hash, compare, or return bearer
values. Audit targets use `InvitationId`, status transitions, actor/correlation identifiers, and
bounded reason codes only. Delivery outcomes never update `Invitation`.

## Testing Strategy

Use strict TDD: failing domain/marker tests first, port-fake application tests, then Testcontainers
PostgreSQL tests. Verify terminal/expiry boundaries, illegal metadata, source binding, duplicate
active email, CAS loss, rollback after provisioning failure, and delivery failure leaving Invitation
unchanged. Add a two-client acceptance race proving one success, one accepted row, and one membership.
Run architecture, Modulith, backend, and PostgreSQL gates; no endpoint or web test is required.

## Migration / Rollout

Use an additive Liquibase changeSet after `004-create-invitations`; no backfill, rename, drop, or
dual-write. `waitlist_invitations`, `WaitlistInvitation`, legacy admin routes, resend/delivery bridge,
and waitlist history remain untouched until DALLAY-570/565 define migration. Existing acceptance
responses remain compatible. Roll back application and additive constraints together, retaining both
tables.

## Open Questions

None; token handoff and downstream command ownership are explicit dependencies, not design blockers.
