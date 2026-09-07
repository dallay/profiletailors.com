# Proposal: DALLAY-568 — Direct Invitation Admin Commands

## Intent

Enable authorized platform administrators to create and revoke direct invitations without database intervention. Direct invitations are independent of the waitlist and produce auditable, revocable authorization to register.

## Scope

### In Scope
- `POST /api/admin/invitations` — create a direct invitation for a normalized email
- `POST /api/admin/invitations/{id}/revoke` — revoke an active invitation
- Reject duplicate active invitations for the same normalized email (409 Conflict)
- Emit `INVITATION_CREATED` and `INVITATION_REVOKED` audit events (no raw tokens)
- Schedule `InvitationNotificationRequested` after successful creation
- Measure creation and revocation counts via platform metrics
- Require `platform.invitations.create` and `platform.invitations.revoke` permissions respectively

### Out of Scope
- Resend behavior (DALLAY-565)
- Waitlist-origin invitations
- Full invitation management UI

## Approach

Build on the existing `Invitation` aggregate (DALLAY-564) and admin authorization boundary (DALLAY-563). Add `platform.invitations.create` to the permission registry — it is currently absent (only READ, RESEND, REVOKE exist). Handlers live in `platformadmin` bounded context, call `InvitationRepository` for conditional persistence, and delegate notification scheduling to the notification seam. Raw invitation tokens never cross API boundaries.

| Area | Impact | Description |
|------|--------|-------------|
| `platformadmin` infrastructure | Modified | New `CreateInvitationHandler` and `RevokeInvitationHandler` |
| `platformadmin` controller | Modified | `POST /api/admin/invitations` and `POST .../revoke` |
| `InvitationRepository` port | No change | Existing conditional transitions suffice |
| Permission registry | Modified | Add `platform.invitations.create` |
| Audit event publication | Modified | Emit domain events; downstream publishes to audit store |
| Observability | Modified | Increment invitation creation/revocation counters |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| `platform.invitations.create` permission gap not caught during design | Low | Declare the gap explicitly; spec phase fills it |
| Notification scheduling failure does not roll back invitation | Low | Creation is persisted first; notification failure is async and retried |
| Concurrent duplicate creation for same email | Low | Conditional INSERT with unique index on `(workspace, normalized_email, status='ACTIVE')` |

## Rollback Plan

1. Deploy previous artifact build
2. Revert migration adding the unique index on active invitations per workspace+email
3. Remove `platform.invitations.create` from the permission registry if it was added
4. Archive the two new endpoints behind a feature flag to preserve the aggregate

## Dependencies

- DALLAY-563 (admin authorization boundary) — COMPLETED
- DALLAY-562 (admin audit event infrastructure) — COMPLETED
- DALLAY-564 (Invitation aggregate as first-class domain) — COMPLETED

## Success Criteria

- [ ] `POST /api/admin/invitations` creates an `ACTIVE` invitation and returns 201 with `id` and `status`
- [ ] Same normalized email within same workspace returns 409 when an active invitation exists
- [ ] `POST /api/admin/invitations/{id}/revoke` transitions invitation to `REVOKED` and returns 200
- [ ] Revoked invitation token is unusable at acceptance gate
- [ ] `INVITATION_CREATED` audit event is published on success (no raw token)
- [ ] `INVITATION_REVOKED` audit event is published on revocation
- [ ] Metrics counter increments on both operations
- [ ] Unauthenticated or unauthorized requests return 401/403 respectively
