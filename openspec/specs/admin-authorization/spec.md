# Admin Authorization Specification

## Purpose

This spec documents the Back Office (`/api/admin/**`) permission model. It formalizes the permission
registry, role taxonomy, role-permission mapping, default-deny enforcement, and the
`OperatorAccessResolver` behavioral contract for administrative access control.

## Permission Registry

All 21 `PlatformPermission` keys and their meanings:

| Key                              | Description                                                                                    |
|----------------------------------|------------------------------------------------------------------------------------------------|
| `platform.dashboard.read`        | View platform dashboard metrics                                                                |
| `platform.waitlist.read`         | Read waitlist entries                                                                          |
| `platform.waitlist.invite`       | Convert waitlist entries to invitations                                                        |
| `platform.waitlist.cancel`       | Cancel waitlist entries                                                                        |
| `platform.invitations.read`      | Read invitations                                                                               |
| `platform.invitations.create`    | Create direct invitations                                                                      |
| `platform.invitations.resend`    | Resend existing invitations                                                                    |
| `platform.invitations.revoke`    | Revoke active invitations                                                                      |
| `platform.users.read`            | Read user profiles                                                                             |
| `platform.users.workspaces.read` | Read workspace membership for a user                                                           |
| `platform.users.deactivate`      | Retained registry key; no control endpoint enforces it (superseded by `platform.users.manage`) |
| `platform.users.reactivate`      | Retained registry key; no control endpoint enforces it (superseded by `platform.users.manage`) |
| `platform.users.manage`          | Disable/enable accounts and revoke sessions                                                    |
| `platform.audit.read`            | Read audit logs                                                                                |
| `platform.operators.read`        | Read platform operator assignments                                                             |
| `platform.operators.manage`      | Create and revoke platform operator role assignments                                           |
| `platform.publishing.stale.read` | Read stale publishing job status                                                               |
| `platform.configuration.read`    | Read operational configuration (registration mode)                                             |
| `platform.configuration.manage`  | Change operational configuration (OWNER-only)                                                  |
| `platform.notifications.read`    | Read notification delivery records                                                             |
| `platform.notifications.manage`  | Retry eligible failed notifications                                                            |

## Role Taxonomy

| Role                | Purpose                                                                   |
|---------------------|---------------------------------------------------------------------------|
| `PLATFORM_OWNER`    | Full platform access; all permissions                                     |
| `PLATFORM_OPERATOR` | Day-to-day platform operations; all permissions except `operators.manage` |
| `SUPPORT_AGENT`     | Customer support read access                                              |
| `AUDITOR`           | Read-only audit and investigation access                                  |

## Role-Permission Mapping

`PLATFORM_ROLE_PERMISSIONS` defines which permissions each role holds:

| Permission                       | OWNER | OPERATOR | SUPPORT_AGENT | AUDITOR |
|----------------------------------|:-----:|:--------:|:-------------:|:-------:|
| `platform.dashboard.read`        |  ✓   |    ✓    |       —       |   ✓    |
| `platform.waitlist.read`         |  ✓   |    ✓    |      ✓       |   ✓    |
| `platform.waitlist.invite`       |  ✓   |    ✓    |       —       |    —    |
| `platform.waitlist.cancel`       |  ✓   |    ✓    |       —       |    —    |
| `platform.invitations.read`      |  ✓   |    ✓    |       —       |    —    |
| `platform.invitations.create`    |  ✓   |    ✓    |       —       |    —    |
| `platform.invitations.resend`    |  ✓   |    ✓    |       —       |    —    |
| `platform.invitations.revoke`    |  ✓   |    ✓    |       —       |    —    |
| `platform.users.read`            |  ✓   |    ✓    |      ✓       |   ✓    |
| `platform.users.workspaces.read` |  ✓   |    ✓    |      ✓       |    —    |
| `platform.users.deactivate`      |  ✓   |    ✓    |       —       |    —    |
| `platform.users.reactivate`      |  ✓   |    ✓    |       —       |    —    |
| `platform.users.manage`          |  ✓   |    ✓    |       —       |    —    |
| `platform.audit.read`            |  ✓   |    ✓    |       —       |   ✓    |
| `platform.operators.read`        |  ✓   |    ✓    |       —       |   ✓    |
| `platform.operators.manage`      |  ✓   |    —     |       —       |    —    |
| `platform.publishing.stale.read` |  ✓   |    ✓    |       —       |    —    |
| `platform.configuration.read`    |  ✓   |    ✓    |       —       |   ✓    |
| `platform.configuration.manage`  |  ✓   |    —     |       —       |    —    |
| `platform.notifications.read`    |  ✓   |    ✓    |      ✓       |   ✓    |
| `platform.notifications.manage`  |  ✓   |    ✓    |       —       |    —    |

## Default-Deny Enforcement

The system MUST enforce default-deny for all administrative operations.

- Any principal without an active `PlatformRoleAssignment` holds **no permissions**.
- `OperatorAccessResolver.resolve()` returns `OperatorAccess(principalId, emptySet())` when no
  active role assignment exists for the principal.
- Controllers that guard `/api/admin/**` endpoints MUST throw `PlatformAccessDeniedException` when
  the effective permission set does not contain the required permission.

## OperatorAccessResolver Behavioral Contract

`OperatorAccessResolver.resolve(principal: PrincipalContext): OperatorAccess`

| Input condition                                                   | Return                                                                          |
|-------------------------------------------------------------------|---------------------------------------------------------------------------------|
| Principal has one or more active `PlatformRoleAssignment` records | `OperatorAccess(principalId, roles)` where `roles` is the set of assigned roles |
| Principal has no `PlatformRoleAssignment` record                  | `OperatorAccess(principalId, emptySet())`                                       |
| Principal has only revoked `PlatformRoleAssignment` records       | `OperatorAccess(principalId, emptySet())`                                       |

`findActiveByPrincipalId` excludes any assignment where `revokedAt IS NOT NULL`.

Effective permissions for a principal are derived by calling `roles.effectivePermissions()` which
applies `PLATFORM_ROLE_PERMISSIONS` to produce the allowed `Set<PlatformPermission>`.

## Frontend Mirror Matches Server

The frontend `ROLE_PERMISSIONS` mirror MUST equal the server `PLATFORM_ROLE_PERMISSIONS` for every
key, including `platform.publishing.stale.read` for OWNER and OPERATOR,
`platform.configuration.read`/`platform.configuration.manage` per the mapping above (OWNER both;
OPERATOR and AUDITOR read-only; SUPPORT_AGENT neither), and
`platform.notifications.read`/`platform.notifications.manage` per the mapping above (OWNER and
OPERATOR both; SUPPORT_AGENT and AUDITOR read-only). The system MUST NOT imply permissions the API
does not enforce.

- GIVEN OWNER or OPERATOR session permissions, WHEN the frontend evaluates
  `hasPermission('platform.publishing.stale.read')`, THEN it returns true, matching the server map.
- GIVEN a planned area with no backing admin API, WHEN its placeholder renders, THEN no permission
  beyond the registry entry is implied or checked. Planned placeholders reuse only existing
  server-enforced keys (overview → `platform.dashboard.read`;
  governance → `platform.operators.read`). The `configuration` nav entry is `live`, gated on its own
  `platform.configuration.read`, backed by a real admin API (dallay/profiletailors.com#672). The
  `notifications` nav entry is `live`, gated on its own `platform.notifications.read`, backed by a
  real admin API (dallay/profiletailors.com#670).

## Frontend Gating Is Additive Only

The frontend MUST treat gating as display convenience only; the server (`OperatorAccessResolver`,
default-deny) SHALL remain authoritative.

- GIVEN a principal lacking a permission who forces client-side nav, WHEN calling the corresponding
  `/api/admin/**` endpoint, THEN the server denies with 401/403 or `PlatformAccessDeniedException`.

## Scenarios

### Scenario: Authorized admin access

- GIVEN a principal with an active `PLATFORM_OPERATOR` assignment
- WHEN `OperatorAccessResolver.resolve()` is called
- THEN the returned `OperatorAccess` contains `PLATFORM_OPERATOR`
- AND the effective permissions include `WAITLIST_INVITE`, `INVITATIONS_RESEND`, and all other
  operator permissions

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

- GIVEN a principal with two assignments: one active `SUPPORT_AGENT` and one revoked
  `PLATFORM_OPERATOR`
- WHEN `OperatorAccessResolver.resolve()` is called
- THEN only the active `SUPPORT_AGENT` is returned
- AND effective permissions reflect only `SUPPORT_AGENT` permissions

### Requirement: Bulk fail-fast permission check (DALLAY-665)

The bulk endpoint MUST check `platform.waitlist.invite` once up front and throw
`PlatformAccessDeniedException` (HTTP 403) before touching any entry when the permission is missing.
No new permission is introduced; role mapping is unchanged.

#### Scenario: Missing permission fails fast

- GIVEN a principal with no `WAITLIST_INVITE` permission
- WHEN the principal calls the bulk endpoint
- THEN the response is 403 and no entry state, invitation, or audit row changes

#### Scenario: Read-only role denied

- GIVEN a principal with only `SUPPORT_AGENT` assignment
- WHEN the principal calls the bulk endpoint
- THEN the response is 403 under default-deny

### Requirement: Platform configuration permission keys (dallay/profiletailors.com#672)

The permission registry MUST add two keys governing the `platform-configuration` capability:
`platform.configuration.read` and `platform.configuration.manage`. `PLATFORM_OWNER` MUST hold both.
`PLATFORM_OPERATOR` MUST hold only `platform.configuration.read`. `AUDITOR` MUST hold only
`platform.configuration.read`, consistent with its existing `platform.operators.read` grant — both
are read-only governance/investigation visibility over operational state AUDITOR already receives.
`SUPPORT_AGENT` MUST hold neither key, consistent with it holding no `platform.operators.*`
permission today. No role other than `PLATFORM_OWNER` MUST hold `platform.configuration.manage`.

| Permission | OWNER | OPERATOR | SUPPORT_AGENT | AUDITOR |
|------------|:-----:|:--------:|:-------------:|:-------:|
| `platform.configuration.read` | ✓ | ✓ | — | ✓ |
| `platform.configuration.manage` | ✓ | — | — | — |

#### Scenario: Read permission is granted to OWNER, OPERATOR, and AUDITOR

- GIVEN an operator with an active `PLATFORM_OWNER`, `PLATFORM_OPERATOR`, or `AUDITOR` assignment
- WHEN effective permissions are evaluated
- THEN `platform.configuration.read` is present

#### Scenario: Manage permission is OWNER-only

- GIVEN an operator with an active `PLATFORM_OPERATOR`, `SUPPORT_AGENT`, or `AUDITOR` assignment
- WHEN effective permissions are evaluated
- THEN `platform.configuration.manage` is absent
- AND any attempt to change registration mode is denied with the established forbidden response

#### Scenario: Configuration nav entry is gated on its own permission

- GIVEN an operator holds `platform.configuration.read` but not `platform.operators.read`
- WHEN the frontend evaluates visibility of the `configuration` nav entry
- THEN the entry is visible, gated solely on `platform.configuration.read`

#### Scenario: Forcing hidden nav still hits server enforcement

- GIVEN a principal lacking `platform.configuration.read` forces client-side navigation to the
  configuration view
- WHEN the corresponding `/api/admin/**` configuration endpoint is called
- THEN the server denies with `401`/`403` regardless of client-side navigation state

### Requirement: Platform notification permission keys (dallay/profiletailors.com#670)

The permission registry MUST include `platform.notifications.read` and
`platform.notifications.manage`. `PLATFORM_OWNER` and `PLATFORM_OPERATOR` MUST hold both.
`AUDITOR` and `SUPPORT_AGENT` MUST hold only `platform.notifications.read`. No other role MUST
hold `platform.notifications.manage`.

`GET /api/admin/notifications` and `GET /api/admin/notifications/{id}` MUST enforce
`platform.notifications.read`. `POST /api/admin/notifications/{id}/retry` MUST enforce
`platform.notifications.manage`. Eligible retry MUST return HTTP 200 (shipped contract;
`platform-notifications` REQ-PN-003).

| Permission | OWNER | OPERATOR | SUPPORT_AGENT | AUDITOR |
|------------|:-----:|:--------:|:-------------:|:-------:|
| `platform.notifications.read` | ✓ | ✓ | ✓ | ✓ |
| `platform.notifications.manage` | ✓ | ✓ | — | — |

#### Scenario: AUDITOR can query notifications

- GIVEN an operator with an active `AUDITOR` assignment
- WHEN the operator queries `GET /api/admin/notifications`
- THEN the response status is 200
- AND the response contains paginated notifications

#### Scenario: Query denied without read permission

- GIVEN a principal that lacks `platform.notifications.read`
- WHEN the principal queries `GET /api/admin/notifications`
- THEN the response status is 403

#### Scenario: OPERATOR can retry eligible notifications

- GIVEN an operator with an active `PLATFORM_OPERATOR` assignment
- AND notification `abc-123` is eligible for retry
- WHEN the operator posts `POST /api/admin/notifications/abc-123/retry`
- THEN the response status is 200
- AND retry is dispatched

#### Scenario: AUDITOR cannot retry notifications

- GIVEN an operator with an active `AUDITOR` assignment (has read, lacks manage)
- AND notification `abc-123` is eligible for retry
- WHEN the operator posts `POST /api/admin/notifications/abc-123/retry`
- THEN the response status is 403

#### Scenario: Permission registry includes notification permissions

- GIVEN the `PlatformPermission` registry is loaded
- WHEN the system initializes
- THEN the registry contains `platform.notifications.read`
- AND the registry contains `platform.notifications.manage`

#### Scenario: OWNER has notification permissions

- GIVEN an operator with an active `PLATFORM_OWNER` assignment
- WHEN effective permissions are evaluated
- THEN `platform.notifications.read` is present
- AND `platform.notifications.manage` is present

#### Scenario: AUDITOR has read-only notification access

- GIVEN an operator with an active `AUDITOR` assignment
- WHEN effective permissions are evaluated
- THEN `platform.notifications.read` is present
- AND `platform.notifications.manage` is absent
