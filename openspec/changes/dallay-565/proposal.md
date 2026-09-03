# Proposal: Invitation Notification Delivery for Admin Operations

## Intent

DALLAY-565 connects DALLAY-564's standalone Invitation to email delivery without making transport
outcome part of invitation validity. It replaces the current waitlist-oriented delivery ownership
and reverse outcome bridge with a Notifications-owned operational seam.

## Scope

### In Scope

- Schedule a notification after persistence with `AFTER_COMMIT`. This is best-effort, not guaranteed
  delivery; a durable outbox is future work.
- Make Notifications own delivery attempts, status, and timestamps. One Invitation may have many
  Notifications: one command idempotency key creates one delivery, while each new resend command
  creates another delivery for the same Invitation.
- Expose a narrow backend/admin read composition correlated by Invitation identity.
- Define non-destructive compatibility handling for legacy waitlist rows, columns, and responses.
- Add unit, transaction, integration, security, and acceptance coverage.

### Out of Scope

Waitlist conversion/bulk operations (DALLAY-570), generic notification queries or retry UI
(DALLAY-574), token lifecycle implementation (DALLAY-566), provider setup (DALLAY-519), and an
outbox.

## Capabilities

### New Capabilities

- `invitation-notification-delivery`: scheduling, ownership, correlation,
  resend/idempotency, admin visibility, and token-safe boundaries.

### Modified Capabilities

- `email-notifications`: invitation trigger timing and token-safe observability/persistence.

## Approach

Use the DALLAY-564 Invitation ID as durable cross-context correlation. An after-commit trigger invokes
Notifications, which creates and updates its delivery record. Remove writes that copy delivery state
back into Invitation. Admin reads compose lifecycle data with a narrow Notifications summary via an
explicit port/read contract. `WaitlistInvitation` is not the target model.

### Unresolved Token-Boundary Assumptions

Raw bearer tokens MUST NOT enter durable events, logs, audit, metrics, or persistence. The ephemeral
handoff, generator/consumer ownership, validation boundary, TTL, and non-secret correlation form are
unresolved and MUST be designed with DALLAY-566; this proposal selects no mechanism.

## Migration & Compatibility

DALLAY-564 MUST land first; DALLAY-565 is blocked by it. New flows target Invitation. No destructive
column removal, legacy backfill/redaction, or incompatible admin response change is authorized until
historical reads and rollback behavior are specified and tested.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| Platformadmin handlers, persistence, reads | Modified | Invitation identity; no delivery ownership. |
| Notifications events, consumer, templates, repository | Modified | Post-commit trigger and delivery records. |
| Liquibase, transaction policy, tests | Modified | Compatibility and security verification. |

## Testing Strategy

Unit tests cover invariants, idempotency, and resend multiplicity. R2DBC/WebFlux integration tests
cover commit/rollback scheduling and composed reads. Security/serialization tests prove token absence.
Cucumber covers scheduling, delivery failure independence, and resend behavior.

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Post-commit crash loses in-memory trigger | Medium | Document limitation; follow up with outbox. |
| Legacy token-bearing data complicates migration | High | Preserve rows; require reviewed migration evidence. |
| Token boundary remains ambiguous | High | Block implementation decisions on DALLAY-566. |

## Rollback Plan

Revert trigger/read-composition and non-destructive schema changes; retain legacy rows. Never restore
delivery fields to canonical Invitation.

## Dependencies

- DALLAY-564 first; DALLAY-566 defines token handoff; DALLAY-519 enables production delivery.
- DALLAY-570 and DALLAY-574 consume this seam later.

## Success Criteria

- [ ] Commit schedules one Notification; rollback schedules none, with best-effort semantics explicit.
- [ ] Failure leaves Invitation valid; same key deduplicates; a new resend adds one delivery.
- [ ] Admin visibility comes from Notifications and no raw bearer token crosses a durable boundary.
