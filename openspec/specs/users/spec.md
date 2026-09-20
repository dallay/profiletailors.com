# Users Management Specification

## Purpose

This spec defines the back-office user management capability for platform operators. It covers the
user list with exact-email search and account-state filters, the user detail view with memberships,
explicit disable/enable/session-revoke commands guarded by `platform.users.manage` and
`Idempotency-Key`, and detail-view actions with confirmation. All behavior mirrors the invitations
and waitlist slice patterns.

---

## Requirements

### Requirement: User list renders with filters and enriched columns

`GET /api/admin/users` MUST support `status` (account state), `email` (exact normalized match),
`createdFrom`, and `createdTo` query parameters with pagination and sorting. The response MUST
include `workspaceCount` per entry.

The UI MUST render a filter bar with status dropdown, date-range inputs, and email search above
the list.

#### Scenario: Operator sees users filtered by account state

- GIVEN an authenticated platform operator with `platform.users.read`
- WHEN the operator opens the users list
- THEN the system SHALL render a filter bar with status dropdown, date-range inputs, and email
  search
- AND SHALL render rows with columns including email, account state, and workspace count

#### Scenario: Operator searches users by email

- GIVEN a populated users list with `email` parameter supported by the backend
- WHEN the operator types a full email in the search field
- THEN the system SHALL filter rows by exact normalized-email match

#### Scenario: Unauthenticated request returns 401

- GIVEN no bearer token
- WHEN `GET /api/admin/users` is invoked
- THEN the system SHALL return `401 Unauthorized`

#### Scenario: Insufficient permission returns 403

- GIVEN a principal without `platform.users.read`
- WHEN `GET /api/admin/users` is invoked
- THEN the system SHALL return `403 Forbidden`

---

### Requirement: User detail renders with all fields, consent, auth methods, and memberships

`GET /api/admin/users/{principalId}` MUST return `consentReceipt`, `marketingConsent`,
`authenticationMethods`, `version`, `workspaceCount`, and `lastAuthenticatedAt` in addition to
the base user fields.

The UI MUST render a detail view with sections for profile, consent, authentication methods,
platform roles, and workspace memberships.

#### Scenario: Operator views user detail

- GIVEN an authenticated operator with `platform.users.read`
- WHEN the operator navigates to a user detail URL
- THEN the system SHALL render sections for: profile fields, consent status, authentication methods
  list, platform roles, workspace memberships
- AND SHALL display `lastAuthenticatedAt` and `workspaceCount`

#### Scenario: User not found returns 404

- GIVEN an authenticated operator with `platform.users.read`
- WHEN `GET /api/admin/users/{nonexistent-id}` is invoked
- THEN the system SHALL return `404 Not Found`

#### Scenario: Unauthenticated request returns 401

- GIVEN no bearer token
- WHEN `GET /api/admin/users/{principalId}` is invoked
- THEN the system SHALL return `401 Unauthorized`

---

### Requirement: Disable action is explicit and idempotent

`POST /api/admin/users/{principalId}/disable` MUST require `platform.users.manage` permission and
a valid `Idempotency-Key` header. The system MUST persist `DISABLED` account state and revoke all
active refresh sessions before reporting success, and MUST NOT report success on partial
completion. Repeating disable on an already-disabled account MUST succeed without changing the
final state. The system MUST emit a `USER_DISABLED` audit event on success.

#### Scenario: Operator disables user successfully

- GIVEN an authenticated operator with `platform.users.manage`
- AND an active user with zero or more active refresh sessions
- WHEN `POST /api/admin/users/{principalId}/disable` is sent with a valid `Idempotency-Key`
- THEN the account SHALL become `DISABLED`
- AND all active refresh sessions SHALL be revoked before success is reported
- AND a `USER_DISABLED` audit event SHALL be emitted

#### Scenario: Disabled account cannot authenticate

- GIVEN a disabled user
- WHEN local login or session refresh is attempted
- THEN authentication SHALL be rejected
- AND no access token or replacement session SHALL be issued

#### Scenario: Insufficient permission returns 403

- GIVEN a principal without `platform.users.manage`
- WHEN `POST /api/admin/users/{principalId}/disable` is invoked
- THEN the system SHALL return `403 Forbidden`
- AND SHALL NOT modify the account

---

### Requirement: Enable action restores access without restoring sessions

`POST /api/admin/users/{principalId}/enable` MUST require `platform.users.manage` permission and
a valid `Idempotency-Key` header. The system MUST persist `ACTIVE` account state; previously
revoked sessions MUST remain revoked. The system MUST emit a `USER_ENABLED` audit event on
success.

#### Scenario: Operator enables user successfully

- GIVEN an authenticated operator with `platform.users.manage`
- AND a disabled user
- WHEN `POST /api/admin/users/{principalId}/enable` is sent with a valid `Idempotency-Key`
- THEN the account SHALL become `ACTIVE`
- AND previously revoked sessions SHALL remain revoked
- AND a `USER_ENABLED` audit event SHALL be emitted

---

### Requirement: Standalone session revocation reports the revoked count

`POST /api/admin/users/{principalId}/sessions/revoke` MUST require `platform.users.manage`
permission and a valid `Idempotency-Key` header. The system MUST revoke all active refresh
sessions without changing account state and MUST return the number revoked. The system MUST emit
a `USER_SESSIONS_REVOKED` audit event on success.

#### Scenario: Operator revokes all sessions

- GIVEN an authenticated operator with `platform.users.manage`
- AND a user with active refresh sessions on multiple devices
- WHEN `POST /api/admin/users/{principalId}/sessions/revoke` is sent
- THEN every active refresh session SHALL become invalid
- AND the response SHALL report the revoked count

---

### Requirement: Detail-view actions with confirmation dialog

The UserDetailView MUST render disable, enable, and revoke-sessions action buttons based on
current account state, visible only to operators holding `platform.users.manage`. Clicking an
action MUST show a confirmation dialog describing the effect. On confirm, the action MUST call
the corresponding POST endpoint with an `Idempotency-Key` header and handle failure by refreshing
the detail data with a toast.

#### Scenario: Operator disables user from detail view

- GIVEN an operator viewing a user detail whose account state is ACTIVE
- WHEN the operator clicks the disable action and confirms
- THEN the system SHALL call `POST /api/admin/users/{principalId}/disable` with an `Idempotency-Key`
- AND on success SHALL update the detail account state to DISABLED

#### Scenario: Failed action refreshes data

- GIVEN an operator viewing a user detail
- WHEN a control action fails
- THEN the system SHALL refresh the detail from the backend
- AND SHALL display a toast indicating the outcome

#### Scenario: Enable action shown only for disabled users

- GIVEN an operator viewing a user detail
- WHEN the account state is DISABLED
- THEN the system SHALL render an enable action button
- AND SHALL NOT render a disable action button

---

### Requirement: List pagination and sorting

`GET /api/admin/users` MUST paginate with `page` and `size` parameters and sort with `sortField`
and `sortDirection`, defaulting to creation time descending. This endpoint MUST require
`platform.users.read` permission.

#### Scenario: Operator pages through users

- GIVEN an authenticated operator with `platform.users.read`
- WHEN `GET /api/admin/users` is invoked with paging and sorting parameters
- THEN the system SHALL return `200 OK` with the requested page in the declared order

#### Scenario: Insufficient permission returns 403

- GIVEN a principal without `platform.users.read`
- WHEN `GET /api/admin/users` is invoked
- THEN the system SHALL return `403 Forbidden`

---

### Requirement: Vitest specs for UsersView and UserDetailView

Vitest suites MUST cover UsersView component rendering with filter interactions, UserDetailView
rendering with all section data, detail-view control actions with confirmation and permission
gating, and toast notifications on action success or failure refresh.

#### Scenario: UsersView renders filter bar

- GIVEN UsersView mounted with mock API returning list data
- WHEN the component renders
- THEN Vitest SHALL verify the filter bar and data rows are present

#### Scenario: UserDetailView renders all sections

- GIVEN UserDetailView mounted with mock API returning full user detail
- WHEN the component renders
- THEN Vitest SHALL verify profile, consent, authentication methods, roles, and memberships sections

#### Scenario: Detail action failure triggers toast and refresh

- GIVEN UserDetailView with a control action pending
- WHEN the action fails
- THEN Vitest SHALL verify a toast is shown and the detail refresh is triggered

---

### Requirement: BDD scenarios for user list, detail, disable, enable, and revocation

Cucumber feature files MUST cover the full user management flow: list rendering with filters,
detail navigation with memberships, disable with session revocation, enable, standalone session
revocation, idempotent replay, and permission denial scenarios.

#### Scenario: List users with all filters applied

- GIVEN an authenticated operator with `platform.users.read`
- WHEN the operator applies status, principalType, date-range, and email filters
- THEN the system SHALL return filtered results matching all criteria

#### Scenario: Disable user revokes sessions

- GIVEN an authenticated operator with `platform.users.manage`
- WHEN the operator disables an active user
- THEN the account SHALL be DISABLED
- AND all active refresh sessions SHALL be revoked
- AND a `USER_DISABLED` audit event SHALL be recorded

#### Scenario: Enable user restores access

- GIVEN an authenticated operator with `platform.users.manage`
- WHEN the operator enables a disabled user
- THEN the account SHALL be ACTIVE
- AND a `USER_ENABLED` audit event SHALL be recorded

#### Scenario: Revoke sessions without state change

- GIVEN an authenticated operator with `platform.users.manage`
- WHEN the operator revokes sessions for a user
- THEN every refresh session SHALL become invalid
- AND a `USER_SESSIONS_REVOKED` audit event SHALL be recorded

#### Scenario: Replay is idempotent

- GIVEN a completed disable command with its idempotency key
- WHEN the same command is replayed
- THEN no duplicate state change or success audit SHALL occur

#### Scenario: Control without permission returns 403

- GIVEN a principal without `platform.users.manage`
- WHEN the operator attempts a user control command
- THEN the system SHALL return `403 Forbidden`
