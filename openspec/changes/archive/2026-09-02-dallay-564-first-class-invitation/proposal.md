# Proposal: First-Class Invitation

## Intent

Make `Invitation` the standalone semantic authorization to register, independent of waitlist and
delivery. The current acceptance slice is partial evidence, not completion: aggregate, lifecycle,
DDD-marker, repository, and concurrency contracts remain incomplete.

## Scope and Non-Goals

### In Scope

- Complete source, normalized-email, timestamp, acceptance, and lifecycle invariants.
- Apply DDD markers and define framework-free application/persistence contracts.
- Add conditional R2DBC transitions, additive schema protections, uniqueness, and race evidence.
- Reconcile affected architecture, data-model, operations, and OpenSpec documentation.

### Out of Scope

- DALLAY-568 admin creation/revocation; DALLAY-570 waitlist conversion and entry state.
- DALLAY-567 registration provisioning; DALLAY-565 notification integration.
- DALLAY-566 concrete secure token lifecycle/handoff.
- Replacing `WaitlistInvitation` flows, full DALLAY-556, UI, bulk operations, or destructive migration.

## Capabilities

### New Capabilities

- `invitations`: identity, source binding, semantic lifecycle, persistence, and exactly-once transitions.

### Modified Capabilities

- None. Existing waitlist, registration, notification, and IAM specs consume this boundary.

## Approach

Use approved Approach 1: complete the existing `Invitation` model and DDD contracts, expose stable
seams for DALLAY-565/566/567/568, and retain legacy flows. Do not create a second invitation or token subsystem.

## Semantic Lifecycle Contract

`ACTIVE` may transition only to `ACCEPTED`, `EXPIRED`, or `REVOKED`; terminal states reject mutation.
`expiresAt` is exclusive. Resolved expiry decision: explicit `expire(at)` materializes `EXPIRED` at
the boundary; scheduling and cleanup are deferred. Resolved ID decision: retain UUID-backed
`InvitationId` as immutable `@ValueObject` with raw PostgreSQL UUID, documented as an infrastructure
exception; no prefixed-ID migration. Non-accepted states reject acceptance metadata.

## Persistence/Application Boundary

Mark `Invitation` as `@AggregateRoot`; mark ID, status, and source as value types. Keep ports
framework-free, map rows in infrastructure, and use the explicit transaction seam for conditional
transitions. Expose semantic state without delivery fields; admin commands remain DALLAY-568.

## Security Ownership

Persist only non-reversible token material and opaque lookup data. Raw tokens, URLs, and delivery state
must not cross Invitation, event, audit, log, or metric boundaries. DALLAY-566 owns token mechanics and
enforcement; DALLAY-565 owns safe notification handoff.

## Compatibility/Migration

`invitations` is canonical for first-class flows. Preserve `waitlist_invitations`, handlers, queries,
history, and delivery bridge until DALLAY-565/570 define migration. No drop, rename, or backfill.

## Testing/TDD Strategy

Strict TDD: failing domain/marker tests first, port fakes, Liquibase integration, and a two-client
PostgreSQL race proving one acceptance. Add token-free audit/metric tests; current acceptance tests
remain partial evidence.

## Architecture/Docs Impacts

Update DDD coverage, C4/data-model/transaction docs, and the private-beta correlation matrix. No web
surface or new endpoint.

## Affected Areas

`platformadmin/domain/Invitation*`, `AcceptInvitation.kt`, invitation ports/adapters,
`004-create-invitations.yaml`, backend/DDD tests, and architecture/operations docs.

## Dependencies

Existing hexagonal, transaction, and DDD contracts. DALLAY-556/565/566/567/568/570 consume or constrain
this contract; DALLAY-565/566 are handoff gates.

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Two tables diverge | Medium | Document ownership; forbid cross-flow substitution. |
| Token boundary unsafe | High | Gate DALLAY-565/566 on explicit contracts. |
| Race guarantee assumed | Medium | Require PostgreSQL contention evidence. |

## Rollback

Revert additive application/schema pieces, retain legacy compatibility, and remove constraints only
after canonical consumers stop.

## Success Criteria

- [ ] Marker, invariant, lifecycle, `EXPIRED`, and one-time race tests pass.
- [ ] Repository/transaction and schema protections are verified.
- [ ] Delivery fields and raw/token-bearing values never cross Invitation boundaries.
- [ ] Docs record UUID, expiry ownership, compatibility, and partial evidence status.
