# Proposal: Expand Backoffice Invitations Management

## Intent

Direct-invitations admin is create-only. Operators cannot discover, filter, or act on existing invitations. Complete the slice: paged list → filter → row resend/revoke.

## Scope

### In Scope
- Paged `GET /api/admin/invitations/direct` (filters `status`, `email`; sort `issuedAt desc`)
- Direct-shaped list summary + `ListAdminDirectInvitationsQuery` (mirror waitlist pattern)
- View table in `DirectInvitationsView.vue` (status filter, email search, pagination, empty/loading/error states)
- Row resend/revoke reuse of existing endpoints
- EN+ES i18n keys; Vitest + BDD list scenarios

### Out of Scope
- Detail route / per-invitation timeline
- Audit deep-link per invitation
- Schema migration (reads existing `invitations` table, `source=DIRECT`)
- Token exposure in any response
- New invitation commands or notification redelivery controls

## Capabilities

### New Capabilities
- None

### Modified Capabilities
- `invitations`: add collection-read requirement (paged direct list; no lifecycle change)
- `backoffice-admin-shell`: direct-invitations view gains list section (nav/permission unchanged)

## Approach

Mirror `ListAdminWaitlistEntriesQuery` / `R2dbcAdminWaitlistQuery.list` + `PagedResult`: new `ListAdminDirectInvitationsQuery`, R2DBC list on `invitations` where `source=DIRECT`, `GET /direct` guarded `INVITATIONS_READ`. Frontend table follows `WaitlistView`/`UsersView` patterns; row actions reuse resend/revoke with `expectedVersion`.

Alternatives: (2) frontend-only ID lookup — rejected, no discovery, wrong summary shape; (3) +detail/audit linkage — deferred, audit entity-filter unverified.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `platformadmin/.../AdminInvitationQuery.kt` | Modified | Add list operation |
| `platformadmin/.../ListAdminDirectInvitationsQuery.kt` + model | New | Direct-shaped summary + query |
| `platformadmin/.../R2dbcAdminInvitationQuery.kt` | Modified | R2DBC list implementation |
| `platformadmin/.../AdminInvitationController.kt` | Modified | `GET /direct` collection |
| `apps/web/admin/src/views/DirectInvitationsView.vue` | Modified | List section, filters, pagination |
| `apps/web/admin/src/i18n/index.ts` | Modified | List/empty-state keys EN+ES |
| `DirectInvitationsView.spec.ts`, `invitations-direct.feature` | Modified | List coverage |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Waitlist-shaped summary reused for direct rows | Med | New direct-shaped summary type |
| Scope assumed from bare "3" input | Med | Confirm list+filter+row-actions, no new commands |
| List permission gap | Low | Guard `GET /direct` with `INVITATIONS_READ`; reuse existing action gates |

## Rollback Plan

Revert commits; no migration involved. View falls back to create-only; endpoints removed are additive-only.

## Dependencies

- Shell nav registry entry done; #659 authz boundary; prior dallay-568/567/565 (audit, guard, email)

## Success Criteria

- [ ] Paged direct list with status/email filters, `issuedAt desc`
- [ ] Row resend/revoke work from table; no token in responses
- [ ] Empty/loading/error states + EN/ES; Vitest + BDD list scenarios green
