# Delta for Admin Authorization

## MODIFIED Requirements

### Requirement: Permission registry and role mapping

The administrative permission registry MUST add `platform.users.manage` for disable, enable, and session-revocation commands. `PLATFORM_OWNER` and `PLATFORM_OPERATOR` MUST hold this permission. `SUPPORT_AGENT`, `AUDITOR`, and principals without an active platform role MUST NOT hold it. `platform.users.read` and `platform.users.workspaces.read` remain independent permissions.
(Previously: the registry exposed user-read and workspace-read permissions only; no explicit permission governed user controls.)

#### Scenario: Control permission is granted narrowly

- GIVEN an operator with an active `PLATFORM_OWNER` or `PLATFORM_OPERATOR` assignment
- WHEN effective permissions are evaluated
- THEN `platform.users.manage` is present
- AND read permissions remain evaluated independently

#### Scenario: Read-only roles cannot mutate users

- GIVEN an operator with `SUPPORT_AGENT`, `AUDITOR`, or no active role assignment
- WHEN the operator invokes disable, enable, or sessions/revoke
- THEN the request is denied with the established forbidden response
- AND no user state or session is changed

### Requirement: Default-deny enforcement

All user-administration query and control endpoints MUST enforce their explicit permission server-side. Missing authentication MUST return the established unauthorized response; an authenticated principal without the required permission MUST return forbidden. Frontend guards MUST NOT be treated as enforcement.
(Previously: default deny covered existing administrative operations but did not define the new user-control permission.)

#### Scenario: Unassigned principal is denied

- GIVEN an authenticated principal has no active platform role assignment
- WHEN that principal requests user administration
- THEN the request is denied
- AND no user data or mutation outcome is disclosed

#### Scenario: Read and control permissions are separate

- GIVEN an operator has `platform.users.read` but not `platform.users.manage`
- WHEN the operator lists users and then requests disable
- THEN listing is allowed
- AND disable is forbidden
