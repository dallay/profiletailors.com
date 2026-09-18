# Admin Authorization Specification

## Purpose

This spec documents the Back Office (`/api/admin/**`) permission model. It formalizes the permission registry, role taxonomy, role-permission mapping, default-deny enforcement, and the `OperatorAccessResolver` behavioral contract for administrative access control.

## Permission Registry

All 16 `PlatformPermission` keys and their meanings:

| Key | Description |
|-----|-------------|
| `platform.dashboard.read` | View platform dashboard metrics |
| `platform.waitlist.read` | Read waitlist entries |
| `platform.waitlist.invite` | Convert waitlist entries to invitations |
| `platform.waitlist.cancel` | Cancel waitlist entries |
| `platform.invitations.read` | Read invitations |
| `platform.invitations.create` | Create direct invitations |
| `platform.invitations.resend` | Resend existing invitations |
| `platform.invitations.revoke` | Revoke active invitations |
| `platform.users.read` | Read user profiles |
| `platform.users.workspaces.read` | Read workspace membership for a user |
| `platform.users.deactivate` | Deactivate a user account and revoke active sessions |
| `platform.users.reactivate` | Reactivate a previously deactivated user account |
| `platform.audit.read` | Read audit logs |
| `platform.operators.read` | Read platform operator assignments |
| `platform.operators.manage` | Create and revoke platform operator role assignments |
| `platform.publishing.stale.read` | Read stale publishing job status |

## Role Taxonomy

| Role | Purpose |
|------|---------|
| `PLATFORM_OWNER` | Full platform access; all permissions |
| `PLATFORM_OPERATOR` | Day-to-day platform operations; all permissions except `operators.manage` |
| `SUPPORT_AGENT` | Customer support read access |
| `AUDITOR` | Read-only audit and investigation access |

## Role-Permission Mapping

`PLATFORM_ROLE_PERMISSIONS` defines which permissions each role holds:

| Permission | OWNER | OPERATOR | SUPPORT_AGENT | AUDITOR |
|------------|:-----:|:--------:|:-------------:|:--------:|
| `platform.dashboard.read` | ✓ | ✓ | — | ✓ |
| `platform.waitlist.read` | ✓ | ✓ | ✓ | ✓ |
| `platform.waitlist.invite` | ✓ | ✓ | — | — |
| `platform.waitlist.cancel` | ✓ | ✓ | — | — |
| `platform.invitations.read` | ✓ | ✓ | — | — |
| `platform.invitations.create` | ✓ | ✓ | — | — |
| `platform.invitations.resend` | ✓ | ✓ | — | — |
| `platform.invitations.revoke` | ✓ | ✓ | — | — |
| `platform.users.read` | ✓ | ✓ | ✓ | ✓ |
| `platform.users.workspaces.read` | ✓ | ✓ | ✓ | — |
| `platform.users.deactivate` | ✓ | ✓ | — | — |
| `platform.users.reactivate` | ✓ | ✓ | — | — |
| `platform.audit.read` | ✓ | ✓ | — | ✓ |
| `platform.operators.read` | ✓ | ✓ | — | ✓ |
| `platform.operators.manage` | ✓ | — | — | — |
| `platform.publishing.stale.read` | ✓ | ✓ | — | — |

## Default-Deny Enforcement

The system MUST enforce default-deny for all administrative operations.

- Any principal without an active `PlatformRoleAssignment` holds **no permissions**.
- `OperatorAccessResolver.resolve()` returns `OperatorAccess(principalId, emptySet())` when no active role assignment exists for the principal.
- Controllers that guard `/api/admin/**` endpoints MUST throw `PlatformAccessDeniedException` when the effective permission set does not contain the required permission.

## OperatorAccessResolver Behavioral Contract

`OperatorAccessResolver.resolve(principal: PrincipalContext): OperatorAccess`

| Input condition | Return |
|----------------|--------|
| Principal has one or more active `PlatformRoleAssignment` records | `OperatorAccess(principalId, roles)` where `roles` is the set of assigned roles |
| Principal has no `PlatformRoleAssignment` record | `OperatorAccess(principalId, emptySet())` |
| Principal has only revoked `PlatformRoleAssignment` records | `OperatorAccess(principalId, emptySet())` |

`findActiveByPrincipalId` excludes any assignment where `revokedAt IS NOT NULL`.

Effective permissions for a principal are derived by calling `roles.effectivePermissions()` which applies `PLATFORM_ROLE_PERMISSIONS` to produce the allowed `Set<PlatformPermission>`.

## Frontend Mirror Matches Server

The frontend `ROLE_PERMISSIONS` mirror MUST equal the server `PLATFORM_ROLE_PERMISSIONS` for every key, including `platform.publishing.stale.read` for OWNER and OPERATOR. The system MUST NOT imply permissions the API does not enforce.

- GIVEN OWNER or OPERATOR session permissions, WHEN the frontend evaluates `hasPermission('platform.publishing.stale.read')`, THEN it returns true, matching the server map.
- GIVEN a planned area with no backing admin API, WHEN its placeholder renders, THEN no permission beyond the registry entry is implied or checked. Planned placeholders reuse only existing server-enforced keys (overview/notifications → `platform.dashboard.read`; governance/configuration → `platform.operators.read`).

## Frontend Gating Is Additive Only

The frontend MUST treat gating as display convenience only; the server (`OperatorAccessResolver`, default-deny) SHALL remain authoritative.

- GIVEN a principal lacking a permission who forces client-side nav, WHEN calling the corresponding `/api/admin/**` endpoint, THEN the server denies with 401/403 or `PlatformAccessDeniedException`.

## Scenarios

### Scenario: Authorized admin access

- GIVEN a principal with an active `PLATFORM_OPERATOR` assignment
- WHEN `OperatorAccessResolver.resolve()` is called
- THEN the returned `OperatorAccess` contains `PLATFORM_OPERATOR`
- AND the effective permissions include `WAITLIST_INVITE`, `INVITATIONS_RESEND`, and all other operator permissions

### Scenario: Unauthorized admin access — no role assignment

- GIVEN a principal with no `PlatformRoleAssignment` record
- WHEN `OperatorAccessResolver.resolve()` is called
- THEN the returned `OperatorAccess` contains an empty role set
- AND `effectivePermissions()` returns an empty permission set
- AND any admin controller requiring a permission throws `PlatformAccessDeniedException`

### Scenario: Unauthorized admin access — permission not held

- GIVEN a principal with an active `SUPPORT_AGENT` assignment
- WHEN the principal attempts to invoke `platform.operators.manage`
- THEN `effectivePermissions()` does not include `OPERATORS_MANAGE`
- AND the admin controller throws `PlatformAccessDeniedException`

### Scenario: Principal with revoked role assignment

- GIVEN a principal whose `PlatformRoleAssignment` has `revokedAt` set to a past instant
- WHEN `OperatorAccessResolver.resolve()` is called
- THEN `findActiveByPrincipalId` returns an empty list (revoked assignments excluded)
- AND the returned `OperatorAccess` contains an empty role set
- AND default-deny applies

### Scenario: Principal with mixed active and revoked assignments

- GIVEN a principal with two assignments: one active `SUPPORT_AGENT` and one revoked `PLATFORM_OPERATOR`
- WHEN `OperatorAccessResolver.resolve()` is called
- THEN only the active `SUPPORT_AGENT` is returned
- AND effective permissions reflect only `SUPPORT_AGENT` permissions

### Requirement: Bulk fail-fast permission check (DALLAY-665)

The bulk endpoint MUST check `platform.waitlist.invite` once up front and throw `PlatformAccessDeniedException` (HTTP 403) before touching any entry when the permission is missing. No new permission is introduced; role mapping is unchanged.

#### Scenario: Missing permission fails fast

- GIVEN a principal with no `WAITLIST_INVITE` permission
- WHEN the principal calls the bulk endpoint
- THEN the response is 403 and no entry state, invitation, or audit row changes

#### Scenario: Read-only role denied

- GIVEN a principal with only `SUPPORT_AGENT` assignment
- WHEN the principal calls the bulk endpoint
- THEN the response is 403 under default-deny
