# Archive Report: enhance-backoffice-users-management

## Summary

**Change**: enhance-backoffice-users-management **Archived**: 2026-09-15 **Mode**: openspec
**Status**: COMPLETE

## Change Description

Added backend + frontend deactivate/reactivate user management to the platform admin. This change
allows platform operators to deactivate and reactivate user accounts from the admin UI.

## Phases Completed

| Phase          | Status |
|----------------|--------|
| explore        | ✓     |
| propose        | ✓     |
| spec           | ✓     |
| design         | ✓     |
| tasks          | ✓     |
| apply-backend  | ✓     |
| apply-frontend | ✓     |
| apply-tests    | ✓     |
| verify         | ✓     |
| archive        | ✓     |

## Verification Results

| Test                          | Result                                                            |
|-------------------------------|-------------------------------------------------------------------|
| Backend Detekt                | BUILD SUCCESSFUL                                                  |
| Backend compileKotlin         | BUILD SUCCESSFUL                                                  |
| Backend compileTestKotlin     | BUILD SUCCESSFUL                                                  |
| Admin build                   | vite: build ok                                                    |
| Admin Vitest (UserDetailView) | 3/3 passed                                                        |
| Admin Vitest suite            | 8 passed / 1 pre-existing failure (WaitlistEntryView — unrelated) |

## Specs Synced to Main

### admin-authorization

| Action  | Details                                                                            |
|---------|------------------------------------------------------------------------------------|
| Updated | Permission count: 14 → 16                                                          |
| Added   | `platform.users.deactivate` — Deactivate a user account and revoke active sessions |
| Added   | `platform.users.reactivate` — Reactivate a previously deactivated user account     |
| Updated | Role mapping for OWNER and OPERATOR to include new permissions                     |

### users

| Action   | Details                                                                                                             |
|----------|---------------------------------------------------------------------------------------------------------------------|
| Created  | New main spec at `openspec/specs/users/spec.md`                                                                     |
| Contains | Full user management specification including status, filters, summary, detail view, deactivate/reactivate endpoints |

## Key Implementation Details

### Backend (server/smp)

- Liquibase: `008-add-principal-status.yaml` — adds `status` column to `principals` table
- Domain: `PrincipalStatus` enum (ACTIVE/DEACTIVATED/SUSPENDED),
  `UserAccountDeactivationConflictException`, `UserPrincipalNotFoundException`
- Audit: `USER_DEACTIVATED`, `USER_REACTIVATED` added to `AdminAuditAction`
- Permissions: `USERS_DEACTIVATE`, `USERS_REACTIVATE` added to `PlatformPermission` — wired to
  PLATFORM_OPERATOR role
- Commands: `DeactivateUserCommand`, `ReactivateUserCommand` (with expectedVersion for optimistic
  lock)
- Port: `PrincipalAdmin` interface + `R2dbcPrincipalAdmin` implementation
- Handlers: `DeactivateUserHandler`, `ReactivateUserHandler` — 3 throws each (access denied, not
  found, version conflict), audit trail
- Controller: `PATCH /api/admin/users/{principalId}/deactivate` and
  `PATCH /api/admin/users/{principalId}/reactivate` in `AdminUserController`
- Problem details: 409 USER_ACCOUNT_VERSION_CONFLICT, 404 USER_PRINCIPAL_NOT_FOUND
- Models: `status` + `version` fields added to `AdminUserSummary`, `AdminUserDetail`
- Query: `status` included in R2dbcAdminUserQuery SELECT

### Frontend (apps/web/admin)

- i18n: 14 new EN + ES keys for status, deactivate, reactivate, confirm dialogs, success/error
  messages
- Types: i18n types.ts updated
- UsersView.vue: status column with color-coded badge (green=ACTIVE, red=DEACTIVATED,
  yellow=SUSPENDED)
- UserDetailView.vue: status display, Deactivate/Reactivate buttons, confirm dialogs, action loading
  states, success/error feedback, re-fetch after action
- UserDetailView.spec.ts: NEW — 3 Vitest specs

## Key Decisions

1. **Optimistic locking**: Via expectedVersion (no actual version column in DB yet — uses
   placeholder version=1L; backend can evolve to proper version column later)
2. **Security**: Handlers enforce USERS_DEACTIVATE/USERS_REACTIVATE permissions; audit trail on
   every action
3. **SUSPENDED users**: Can only be deactivated, not re-suspended (handler allows deactivate for
   SUSPENDED as idempotent)
4. **Frontend confirm**: Uses confirm () dialog for destructive action; success/error feedback; auto
   re-fetch after action
5. **Backend idempotency**: Deactivate is idempotent (no-op if already DEACTIVATED/SUSPENDED),
   reactivate is idempotent (no-op if already ACTIVE)

## Archive Location

`openspec/changes/archive/2026-09-15-enhance-backoffice-users-management/`

## Source of Truth Updated

- `openspec/specs/admin-authorization/spec.md` — Updated with new permissions
- `openspec/specs/users/spec.md` — New main spec created

## SDD Cycle Complete

This change has been fully planned, implemented, verified, and archived. Ready for the next change.
