# Delta for admin-authorization

## ADDED Requirements

### Requirement: User mutation permissions in permission registry

The permission registry MUST include two new `PlatformPermission` keys for user state mutations:

| Key | Description |
|-----|-------------|
| `platform.users.deactivate` | Deactivate a user account and revoke active sessions |
| `platform.users.reactivate` | Reactivate a previously deactivated user account |

These permissions MUST be enforced by the backend endpoint adapters for
`PATCH /api/admin/users/{principalId}/deactivate` and
`PATCH /api/admin/users/{principalId}/reactivate` respectively.

#### Scenario: Deactivate permission registered

- GIVEN the platform permission registry
- WHEN the registry is loaded
- THEN `platform.users.deactivate` SHALL be present with the description "Deactivate a user account and revoke active sessions"

#### Scenario: Reactivate permission registered

- GIVEN the platform permission registry
- WHEN the registry is loaded
- THEN `platform.users.reactivate` SHALL be present with the description "Reactivate a previously deactivated user account"

---

### Requirement: PLATFORM_OWNER and PLATFORM_OPERATOR roles include user mutation permissions

`PLATFORM_ROLE_PERMISSIONS` MUST assign `platform.users.deactivate` and `platform.users.reactivate`
to `PLATFORM_OWNER` and `PLATFORM_OPERATOR` roles. `SUPPORT_AGENT` and `AUDITOR` MUST NOT have
these permissions.

#### Scenario: PLATFORM_OWNER has deactivate and reactivate permissions

- GIVEN the role-permission mapping
- WHEN `PLATFORM_OWNER` role is resolved
- THEN `platform.users.deactivate` SHALL be present in the permission set
- AND `platform.users.reactivate` SHALL be present in the permission set

#### Scenario: PLATFORM_OPERATOR has deactivate and reactivate permissions

- GIVEN the role-permission mapping
- WHEN `PLATFORM_OPERATOR` role is resolved
- THEN `platform.users.deactivate` SHALL be present in the permission set
- AND `platform.users.reactivate` SHALL be present in the permission set

#### Scenario: SUPPORT_AGENT does not have user mutation permissions

- GIVEN the role-permission mapping
- WHEN `SUPPORT_AGENT` role is resolved
- THEN `platform.users.deactivate` SHALL NOT be present
- AND `platform.users.reactivate` SHALL NOT be present

#### Scenario: AUDITOR does not have user mutation permissions

- GIVEN the role-permission mapping
- WHEN `AUDITOR` role is resolved
- THEN `platform.users.deactivate` SHALL NOT be present
- AND `platform.users.reactivate` SHALL NOT be present
