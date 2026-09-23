# Delta for Admin Authorization

## ADDED Requirements

### Requirement: platform.notifications.read permission for notification query operations

(Rationale: REQ-PN-006)

The permission registry MUST include `platform.notifications.read` key with description "Read
notification delivery logs and status".

`OperatorAccessResolver` MUST grant `platform.notifications.read` to roles: OWNER, OPERATOR,
AUDITOR.

`GET /api/admin/notifications` and `GET /api/admin/notifications/{id}` endpoints MUST enforce
`platform.notifications.read` permission via
`@RequiresPlatformPermission("platform.notifications.read")` or equivalent infrastructure.

#### Scenario: AUDITOR can query notifications

```gherkin
GIVEN operator has role AUDITOR
 WHEN operator queries GET /api/admin/notifications
 THEN response status is 200
  AND response contains paginated notifications
```

#### Scenario: VIEWER cannot query notifications

```gherkin
GIVEN operator has role VIEWER (lacks platform.notifications.read)
 WHEN operator queries GET /api/admin/notifications
 THEN response status is 403
  AND response.error contains "Insufficient permissions"
```

---

### Requirement: platform.notifications.manage permission for notification retry operations

(Rationale: REQ-PN-007)

The permission registry MUST include `platform.notifications.manage` key with description "Retry
failed notifications".

`OperatorAccessResolver` MUST grant `platform.notifications.manage` to roles: OWNER, OPERATOR.

`POST /api/admin/notifications/{id}/retry` endpoint MUST enforce `platform.notifications.manage`
permission via `@RequiresPlatformPermission("platform.notifications.manage")`.

#### Scenario: OPERATOR can retry eligible notifications

```gherkin
GIVEN operator has role OPERATOR
  AND notification abc-123 is eligible for retry
 WHEN operator posts POST /api/admin/notifications/abc-123/retry
 THEN response status is 202
  AND retry is dispatched
```

#### Scenario: AUDITOR cannot retry notifications

```gherkin
GIVEN operator has role AUDITOR (has read, lacks manage)
  AND notification abc-123 is eligible for retry
 WHEN operator posts POST /api/admin/notifications/abc-123/retry
 THEN response status is 403
  AND response.error contains "Insufficient permissions"
```

## MODIFIED Requirements

### Requirement: Permission Registry

(Previously: 19 permissions; no notification-scoped permissions)

The permission registry MUST include these 21 permissions:

| Key                                 | Description                                                                                    |
|-------------------------------------|------------------------------------------------------------------------------------------------|
| `platform.dashboard.read`           | View platform dashboard metrics                                                                |
| `platform.waitlist.read`            | Read waitlist entries                                                                          |
| `platform.waitlist.invite`          | Convert waitlist entries to invitations                                                        |
| `platform.waitlist.cancel`          | Cancel waitlist entries                                                                        |
| `platform.invitations.read`         | Read invitations                                                                               |
| `platform.invitations.create`       | Create direct invitations                                                                      |
| `platform.invitations.resend`       | Resend existing invitations                                                                    |
| `platform.invitations.revoke`       | Revoke active invitations                                                                      |
| `platform.users.read`               | Read user profiles                                                                             |
| `platform.users.workspaces.read`    | Read workspace membership for a user                                                           |
| `platform.users.deactivate`         | Retained registry key; no control endpoint enforces it (superseded by `platform.users.manage`) |
| `platform.users.reactivate`         | Retained registry key; no control endpoint enforces it (superseded by `platform.users.manage`) |
| `platform.users.manage`             | Disable/enable accounts and revoke sessions                                                    |
| `platform.audit.read`               | Read audit logs                                                                                |
| `platform.operators.read`           | Read platform operator assignments                                                             |
| `platform.operators.manage`         | Create and revoke platform operator role assignments                                           |
| `platform.publishing.stale.read`    | Read stale publishing job status                                                               |
| `platform.configuration.read`       | Read operational configuration (registration mode)                                             |
| `platform.configuration.write`      | Write operational configuration (registration mode)                                            |
| **`platform.notifications.read`**   | **Read notification delivery logs and status**                                                 |
| **`platform.notifications.manage`** | **Retry failed notifications**                                                                 |

#### Scenario: Permission registry includes notification permissions

```gherkin
GIVEN PlatformPermission enum is loaded
 WHEN system initializes permission registry
 THEN registry contains "platform.notifications.read" with description "Read notification delivery logs and status"
  AND registry contains "platform.notifications.manage" with description "Retry failed notifications"
```

---

### Requirement: Role-Permission Mapping

(Previously: no notification permissions mapped to any role)

`OperatorAccessResolver` role-permission mapping MUST include:

- **OWNER**: all 21 permissions including `platform.notifications.read` and
  `platform.notifications.manage`
- **OPERATOR**: all permissions EXCEPT `platform.operators.manage` (20 permissions including both
  notification permissions)
- **AUDITOR**: `platform.dashboard.read`, `platform.waitlist.read`, `platform.invitations.read`,
  `platform.users.read`, `platform.users.workspaces.read`, `platform.audit.read`,
  `platform.operators.read`, `platform.publishing.stale.read`, `platform.configuration.read`, **
  `platform.notifications.read`**
- **VIEWER**: `platform.dashboard.read`, `platform.waitlist.read`, `platform.invitations.read`,
  `platform.users.read`, `platform.users.workspaces.read`, `platform.configuration.read` (no
  notification permissions)

#### Scenario: OWNER has notification permissions

```gherkin
GIVEN operator has role OWNER
 WHEN OperatorAccessResolver.hasPermission(operatorId, "platform.notifications.read") is invoked
 THEN result is true
 WHEN OperatorAccessResolver.hasPermission(operatorId, "platform.notifications.manage") is invoked
 THEN result is true
```

#### Scenario: AUDITOR has read-only notification access

```gherkin
GIVEN operator has role AUDITOR
 WHEN OperatorAccessResolver.hasPermission(operatorId, "platform.notifications.read") is invoked
 THEN result is true
 WHEN OperatorAccessResolver.hasPermission(operatorId, "platform.notifications.manage") is invoked
 THEN result is false
```

## Technical Notes

- **Frontend Mirror**: Dashboard navigation must reflect `platform.notifications.read` for menu
  visibility and `platform.notifications.manage` for retry button enablement.
- **Permission Check Infrastructure**: Existing `@RequiresPlatformPermission` annotation and
  `OperatorAccessResolver.requirePermission()` enforcement apply without modification.
- **No Backend Role Management Changes**: Role assignment, revocation, and role-permission
  resolution logic unchanged; only permission registry and mapping tables extended.
