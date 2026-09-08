# Proposal: DALLAY-568 — Direct Invitation Admin Commands

## Intent

Enable authorized platform administrators to create and revoke direct invitations without database intervention. Direct invitations are independent of the waitlist and produce auditable, revocable authorization to register.

## Scope

### In Scope
- `POST /api/admin/invitations/direct` — create a direct invitation for a normalized email
- `POST /api/admin/invitations/{id}/direct-revoke` — revoke an active invitation with optimistic-lock version
- `POST /api/admin/invitations/{id}/direct-resend` — rotate token material for a DIRECT, still-valid invitation (separate `platform.invitations.resend` permission)
- Reject duplicate active invitations for the same normalized email (409 Conflict); expired rows never block replacements
- Create/resend responses return `{invitationId, status, expiresAt, version}` — NO token field; callers use the returned version for later revokes
- Emit `INVITATION_CREATED`, `INVITATION_REVOKED`, `INVITATION_RESENT` audit events (no raw tokens)
- Deliver the accept URL via the existing `InvitationIssued` / `DirectInvitationResent` post-commit email seam
- Measure creation and revocation counts via platform metrics
- Require `platform.invitations.create`, `.revoke`, `.resend` permissions respectively

### Out of Scope
- Waitlist-origin invitations (keep their own resend path with limits)
- Full invitation management UI

## Approach

Build on the existing `Invitation` aggregate (DALLAY-564) and admin authorization boundary (DALLAY-563). Add `platform.invitations.create` to the permission registry — it is currently absent (only READ, RESEND, REVOKE exist). Handlers live in `platformadmin` bounded context, call `InvitationRepository` for persistence, and deliver notifications through the existing `InvitationIssued` post-commit email seam. Raw invitation tokens never cross API boundaries.

| Area | Impact | Description |
|------|--------|-------------|
| `platformadmin` infrastructure | Modified | New `CreateInvitationHandler`, `RevokeInvitationHandler`, `ResendInvitationHandler` |
| `platformadmin` controller | Modified | `POST /api/admin/invitations/direct` and `POST .../direct-revoke`, `POST .../direct-resend` |
| `InvitationRepository` port | Modified | `hasActiveInvitationFor` gains the `asOf` instant so expired rows never block replacements |
| Permission registry | Modified | Add `platform.invitations.create` |
| Audit event publication | Modified | Emit domain events; downstream publishes to audit store |
| Observability | Modified | Increment invitation creation/revocation counters |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| `platform.invitations.create` permission gap not caught during design | Low | Declare the gap explicitly; spec phase fills it |
| Notification scheduling failure does not roll back invitation | Low | Event is published after persistence; a publish failure propagates without rolling back the saved invitation (pinned by test) |
| Concurrent duplicate creation for same email | Low | Pre-check query plus the pre-existing partial unique index as backstop; `save()` maps the conflict to 409 |

## Rollback Plan

1. Deploy previous artifact build
2. No schema rollback required — this change adds no migration (the unique index predates it)
3. Remove `platform.invitations.create` from the permission registry if it was added
4. Archive the new endpoints behind a feature flag to preserve the aggregate

## Dependencies

- DALLAY-563 (admin authorization boundary) — COMPLETED
- DALLAY-562 (admin audit event infrastructure) — COMPLETED
- DALLAY-564 (Invitation aggregate as first-class domain) — COMPLETED

## Success Criteria

- [ ] `POST /api/admin/invitations/direct` creates an `ACTIVE` invitation and returns 201 with `invitationId`, `status`, `expiresAt`, `version`
- [ ] Same normalized email within same workspace returns 409 when a live active invitation exists; expired rows do not block
- [ ] `POST /api/admin/invitations/{id}/direct-revoke` transitions invitation to `REVOKED` and returns 200
- [ ] `POST /api/admin/invitations/{id}/direct-resend` rotates token material for DIRECT, still-valid invitations and returns the bumped version
- [ ] Revoked invitation token is unusable at acceptance gate
- [ ] `INVITATION_CREATED` audit event is published on success (no raw token)
- [ ] `INVITATION_REVOKED` audit event is published on revocation
- [ ] Metrics counter increments on both operations
- [ ] Unauthenticated or unauthorized requests return 401/403 respectively
