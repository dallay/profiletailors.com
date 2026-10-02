# User Administration Specification

## Purpose

Define the Back Office user list/detail views and narrowly scoped account controls. Controls MUST be
explicit, retry-safe, auditable, and limited to administrative account state and refresh-session
revocation.

## Requirements

### Requirement: List and inspect users

Authorized operators MUST be able to list and search users with pagination and exact
normalized-email matching. Results MUST expose principal identity, account state, verification
state, registration time, and workspace count; detail MUST additionally expose workspace memberships
and platform roles. The existing `status` filter MUST represent account state rather than principal
type.

#### Scenario: Search and list users

- GIVEN an operator has `platform.users.read`
- WHEN the operator requests a paginated list with an exact email filter
- THEN matching users are returned with their account and verification states
- AND pagination and declared sorting are applied

#### Scenario: Inspect user detail and memberships

- GIVEN an operator has `platform.users.read` and `platform.users.workspaces.read`
- WHEN the operator requests an existing user detail
- THEN identity, account state, verification state, registration time, roles, and workspace
  memberships are returned

#### Scenario: Missing user or workspace permission

- GIVEN the target user does not exist, or the operator lacks a required read permission
- WHEN the corresponding detail or workspace request is made
- THEN the service returns the established not-found or forbidden response
- AND MUST NOT disclose protected user data

### Requirement: Control account state and sessions

An operator with `platform.users.manage` MUST be able to `disable`, `enable`, and `sessions/revoke`
through explicit user-targeted commands. Commands MUST be idempotent. Disable MUST persist
`DISABLED` state and revoke all active refresh sessions; enable MUST persist `ACTIVE` state; revoke
MUST revoke all active refresh sessions and return the number revoked. No command MAY edit email,
verification, ownership, memberships, or delete a user.

#### Scenario: Disable and revoke sessions

- GIVEN an active user with zero or more active refresh sessions
- WHEN an authorized operator disables the user
- THEN the account becomes `DISABLED`
- AND all active refresh sessions are revoked before success is reported
- AND repeating disable succeeds without changing the final state

#### Scenario: Enable and explicitly revoke

- GIVEN a disabled user
- WHEN the operator enables the user
- THEN the account becomes `ACTIVE`
- AND existing revoked sessions remain revoked
- WHEN the operator requests session revocation
- THEN all currently active sessions are revoked and the response reports the count

#### Scenario: Unknown target or failed control

- GIVEN the target principal is absent or a required state/session operation fails
- WHEN a control command is submitted
- THEN the service returns the established not-found or server-failure response
- AND MUST NOT report success or emit a success audit outcome

## Decisions

- Default account states are `ACTIVE` and `DISABLED`; no separate suspension state is introduced.
- Control authorization uses one explicit `platform.users.manage` permission, granted only to
  `PLATFORM_OWNER` and `PLATFORM_OPERATOR`; read permissions remain independent.
- Exact normalized-email matching is retained; fuzzy or partial search is not added.
- Disable is fail-closed: success is not returned unless account state and session revocation both
  complete. The design MUST make retries safe; if completion cannot be established, the outcome is
  failure and the account MUST NOT be treated as safely enabled.
- API error bodies and exact metric names remain implementation/design concerns, but status
  semantics MUST preserve existing admin 401/403/404 conventions.
