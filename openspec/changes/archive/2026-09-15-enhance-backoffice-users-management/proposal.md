# Proposal: enhance-backoffice-users-management

## Intent

Provide complete users management for platform operators: enriched list views with filters and
summary metrics, detail views with all user fields and row actions, and backend mutation endpoints
(deactivate/reactivate) with optimistic locking. Closes all 5 HIGH and 5 MEDIUM gaps from
exploration. Mirrors the waitlist/invitations slice patterns for consistency.

## Scope

### In Scope

**Backend — mutation endpoints:**

- `PATCH /api/admin/users/{principalId}/deactivate` — expects `version` header; returns
  `409 Conflict` on version mismatch
- `PATCH /api/admin/users/{principalId}/reactivate` — reverses a prior deactivation; same optimistic
  lock pattern
- `GET /api/admin/users/summary` — returns counts grouped by `status` and `principalType`
- Emit `USER_DEACTIVATED`, `USER_REACTIVATED` audit events
- Require `platform.users.deactivate` / `platform.users.reactivate` permissions

**Backend — query enrichments:**

- Add `version` to `GET /api/admin/users/{principalId}` response
- Add `consentReceipt`, `marketingConsent`, `authenticationMethods` to user detail response
- Add `workspaceCount` and `lastAuthenticatedAt` to list response

**Frontend — UsersView:**

- Status filter dropdown (ACTIVE, INACTIVE, SUSPENDED)
- principalType filter dropdown (USER, SERVICE_ACCOUNT, etc.)
- Date-range filter (createdFrom/createdTo) — backend already supports
- Summary count card (breakdown by principalType and status)
- New list columns: `workspaceCount`, `platformRoles`, `lastAuthenticatedAt`,
  `authenticationMethods`
- Debounced search already wired; ensure parity with filter UX

**Frontend — UserDetailView:**

- Render `authenticationMethods` list
- Render `consentReceipt` and `marketingConsent` fields
- Display `version` field (for operator awareness, non-editable)
- Row action buttons: Deactivate, Reactivate (context-aware, one shown based on status)
- Action dialogs with optimistic lock confirmation (version header sent on submit)
- Conflict error UX (409 → show current data + refresh prompt)

**Testing:**

- Vitest specs for `UsersView` (list, filters, summary card, error/loading states)
- Vitest specs for `UserDetailView` (fields, actions, dialog flow)
- Cucumber BDD scenarios for list users, get user detail, deactivate, reactivate
- BDD for `GET /api/admin/users/summary`

**i18n:**

- Full `users.*` namespace in EN and ES (filters, actions, dialogs, errors, status labels)

**Low-priority feasible items:**

- Copy principalId to clipboard in detail view
- User metadata summary (if `metadata` field exists in model)
- Audit trail linkage (link to audit log view, does not implement audit log itself)

### Out of Scope

- Impersonate action — separate security concern requiring dedicated threat modeling
- Workspace detail drill-down links — separate feature with its own scope
- User creation — separate registration/invitation flow
- Role management mutations (`PATCH /api/admin/users/{principalId}/roles`) — requires platform roles
  definition first
- Backend consent mutation (withdraw/accept) — separate consent flow

## Approach

**Backend-first.** Define and implement mutation endpoints before wiring frontend. Follow the
hexagonal architecture: handlers in `platformadmin` infrastructure, use cases in application,
aggregate/domain in domain.

1. Add `version` field to `Principal` aggregate or query projection
2. Add `platform.users.deactivate` and `platform.users.reactivate` to permission registry
   (`admin-authorization` spec)
3. Implement `DeactivateUserCommand`, `ReactivateUserCommand` handlers
4. Implement `GetUserSummaryQuery`
5. Extend `AdminUserController` with new endpoints; add `version`, `consentReceipt`,
   `authenticationMethods` to existing responses
6. Write unit tests, BDD scenarios for all new endpoints
7. Wire frontend: enrich `UsersView.vue` with filters and summary card
8. Wire frontend: enrich `UserDetailView.vue` with all fields and action dialogs
9. Write Vitest specs for both views
10. Add EN and ES i18n entries

## Capabilities

### New Capabilities

- `user-mutation`: Backend user deactivation and reactivation with optimistic lock and audit events.
  Each becomes `openspec/specs/user-mutation/spec.md`.

### Modified Capabilities

- `iam`: Extend `Principal` query projections with `version`, `consentReceipt`,
  `authenticationMethods`, `workspaceCount`. Delta spec needed.

## Affected Areas

| Area                                                               | Impact   | Description                                                       |
|--------------------------------------------------------------------|----------|-------------------------------------------------------------------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/` | Modified | New command handlers, extended query                              |
| `server/smp/src/main/kotlin/com/profiletailors/smp/iam/`           | Modified | Add `version` to Principal projection; consent/authMethods fields |
| `server/smp/src/main/kotlin/com/profiletailors/smp/authorization/` | Modified | New permission keys + role mappings                               |
| `server/smp/src/test/`                                             | New      | BDD scenarios for user list/detail/mutations                      |
| `apps/web/admin/src/modules/backoffice/`                           | Modified | `UsersView.vue`, `UserDetailView.vue` enriched                    |
| `apps/web/admin/src/i18n/`                                         | Modified | `users.*` EN + ES keys                                            |
| `openspec/specs/admin-authorization/`                              | Modified | New permission entries                                            |
| `openspec/specs/iam/`                                              | Modified | Delta for Principal query fields                                  |

## Risks

| Risk                                                                          | Likelihood | Mitigation                                                                                       |
|-------------------------------------------------------------------------------|------------|--------------------------------------------------------------------------------------------------|
| User deactivation terminates active sessions and invalidates tokens           | High       | Emit `USER_DEACTIVATED` event; ensure token-revocation handler exists before shipping            |
| `consentReceipt` fields on `Principal` may differ from waitlist consent shape | Medium     | Verify exact field names/shapes in `Principal` entity vs waitlist spec before spec phase         |
| No existing pattern for user mutations in platformadmin context               | Low        | Follow invitations/waitlist command handler patterns closely; review with backend-platform skill |

## Rollback Plan

- **Backend**: Revert migration (if any schema change), remove new endpoints, restore previous
  controller responses. Tag in git for precise rollback.
- **Frontend**: Revert `UsersView.vue` and `UserDetailView.vue` to prior commit. No data migration
  needed.
- **Permissions**: Remove new permission keys from registry — role assignments referencing them
  become no-ops (default-deny preserves security).

## Dependencies

- Shell backoffice slice (`backoffice-admin-shell`) — must be complete and stable
- Invitations slice (`invitations`) — reference pattern for admin mutations
- Waitlist slice (`waitlist`) — reference pattern for optimistic lock UX and summary card
- DALLAY-659 (authz boundary for platformadmin context) — permission registry must include new keys
- `Principal` aggregate must expose `version` — verify before spec phase; may need domain change

## Success Criteria

- [ ] `PATCH /api/admin/users/{principalId}/deactivate` returns 200 with updated user and emits
  audit event
- [ ] `PATCH /api/admin/users/{principalId}/reactivate` returns 200 and emits audit event
- [ ] Stale `version` header on deactivate/reactivate returns 409 Conflict
- [ ] `GET /api/admin/users/summary` returns correct counts grouped by status and principalType
- [ ] `GET /api/admin/users/{principalId}` includes `version`, `consentReceipt`,
  `authenticationMethods`
- [ ] UsersView renders status/principalType filters, summary card, and enriched columns
- [ ] UserDetailView renders all user fields and action buttons
- [ ] Action dialog sends `version` header; conflict UX shows 409 with refresh
- [ ] All new endpoints have BDD scenarios
- [ ] All new views have Vitest specs
- [ ] EN and ES i18n keys complete
- [ ] `backend-check` passes; no new Detekt/architecture violations
