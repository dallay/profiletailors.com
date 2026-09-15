# Users Management Specification

## Purpose

This spec defines the back-office user management capability for platform operators. It covers the
enriched user list with filters and summary metrics, the user detail view with all fields, backend
mutation endpoints with optimistic locking, row-level actions with confirmation, and the supporting
backend summary endpoint. All behavior mirrors the invitations and waitlist slice patterns.

---

## Requirements

### Requirement: User list renders with filters, summary card, and enriched columns

`GET /api/admin/users` MUST support `status`, `principalType`, `createdFrom`, `createdTo`, and
`search` (email substring) query parameters. The response MUST include `workspaceCount`,
`platformRoles`, `lastAuthenticatedAt`, and `authenticationMethods` per entry.

The UI MUST render a summary card above the list showing counts grouped by `status` and
`principalType` fetched from `GET /api/admin/users/summary`.

#### Scenario: Operator sees active users filtered by principal type

- GIVEN an authenticated platform operator with `platform.users.read`
- WHEN the operator opens the users list
- THEN the system SHALL render a summary card with counts by status and principalType
- AND SHALL render a filter bar with status dropdown, principalType dropdown, date-range inputs, and email search
- AND SHALL render rows with columns: email, principalType, status, workspaceCount, platformRoles, lastAuthenticatedAt, authenticationMethods

#### Scenario: Operator searches users by email

- GIVEN a populated users list with `search` parameter supported by the backend
- WHEN the operator types a partial email in the search field
- THEN the system SHALL debounce the request and filter rows by email substring match

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
- THEN the system SHALL render sections for: profile fields, consent status, authentication methods list, platform roles, workspace memberships
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

### Requirement: Deactivate action with optimistic locking

`PATCH /api/admin/users/{principalId}/deactivate` MUST require `platform.users.deactivate`
permission. The request MUST include an `If-Match: version` header. On version match the system
MUST deactivate the user and return `200 OK`. On version mismatch the system MUST return
`409 Conflict`. The system MUST emit a `USER_DEACTIVATED` audit event on success.

#### Scenario: Operator deactivates user successfully

- GIVEN an authenticated operator with `platform.users.deactivate`
- AND a user with a known `version`
- WHEN `PATCH /api/admin/users/{principalId}/deactivate` is sent with matching `If-Match` header
- THEN the system SHALL return `200 OK` with updated user payload including new `version`
- AND SHALL emit a `USER_DEACTIVATED` audit event

#### Scenario: Version mismatch returns 409

- GIVEN an authenticated operator with `platform.users.deactivate`
- AND a user whose current `version` differs from the `If-Match` header
- WHEN `PATCH /api/admin/users/{principalId}/deactivate` is sent with stale `If-Match` header
- THEN the system SHALL return `409 Conflict`
- AND SHALL NOT modify the user record

#### Scenario: Missing If-Match header returns 428

- GIVEN an authenticated operator with `platform.users.deactivate`
- WHEN `PATCH /api/admin/users/{principalId}/deactivate` is sent without `If-Match` header
- THEN the system SHALL return `428 Precondition Required`

#### Scenario: Insufficient permission returns 403

- GIVEN a principal without `platform.users.deactivate`
- WHEN `PATCH /api/admin/users/{principalId}/deactivate` is invoked
- THEN the system SHALL return `403 Forbidden`

---

### Requirement: Reactivate action with optimistic locking

`PATCH /api/admin/users/{principalId}/reactivate` MUST require `platform.users.reactivate`
permission. The request MUST include an `If-Match: version` header. On version match the system
MUST reactivate the user and return `200 OK`. On version mismatch the system MUST return
`409 Conflict`. The system MUST emit a `USER_REACTIVATED` audit event on success.

#### Scenario: Operator reactivates user successfully

- GIVEN an authenticated operator with `platform.users.reactivate`
- AND a deactivated user with a known `version`
- WHEN `PATCH /api/admin/users/{principalId}/reactivate` is sent with matching `If-Match` header
- THEN the system SHALL return `200 OK` with updated user payload including new `version`
- AND SHALL emit a `USER_REACTIVATED` audit event

#### Scenario: Version mismatch returns 409

- GIVEN an authenticated operator with `platform.users.reactivate`
- AND a deactivated user whose current `version` differs from the `If-Match` header
- WHEN `PATCH /api/admin/users/{principalId}/reactivate` is sent with stale `If-Match` header
- THEN the system SHALL return `409 Conflict`

#### Scenario: Missing If-Match header returns 428

- GIVEN an authenticated operator with `platform.users.reactivate`
- WHEN `PATCH /api/admin/users/{principalId}/reactivate` is sent without `If-Match` header
- THEN the system SHALL return `428 Precondition Required`

#### Scenario: Insufficient permission returns 403

- GIVEN a principal without `platform.users.reactivate`
- WHEN `PATCH /api/admin/users/{principalId}/reactivate` is invoked
- THEN the system SHALL return `403 Forbidden`

---

### Requirement: Row actions in user list with confirmation dialog

The UsersView MUST render row-level deactivate and reactivate action buttons for each row based on
current user status. Clicking an action MUST show a confirmation dialog describing the effect.
On confirm, the action MUST call the corresponding backend endpoint with the current `version`
from the row and handle 409 by refreshing the row data with the new version.

#### Scenario: Operator deactivates user from list row

- GIVEN an operator viewing the users list with a row whose status is ACTIVE
- WHEN the operator clicks the deactivate action on that row
- THEN the system SHALL show a confirmation dialog
- AND on operator confirm SHALL call `PATCH /api/admin/users/{principalId}/deactivate` with `If-Match` from the row
- AND on `200 OK` SHALL update the row status to INACTIVE

#### Scenario: Optimistic lock conflict on row action refreshes data

- GIVEN an operator viewing the users list
- WHEN a row action returns `409 Conflict`
- THEN the system SHALL refresh the affected row from the list endpoint
- AND SHALL display a toast indicating the data was updated by another operator

#### Scenario: Reactivate action shown only for inactive users

- GIVEN an operator viewing the users list
- WHEN a row status is INACTIVE
- THEN the system SHALL render a reactivate action button
- AND SHALL NOT render a deactivate action button

---

### Requirement: Backend summary endpoint returns counts by status and principal type

`GET /api/admin/users/summary` MUST return a JSON object with counts keyed by `status` and
`principalType`, for example `{ "byStatus": { "ACTIVE": 10, "INACTIVE": 2 }, "byPrincipalType": {
"USER": 11, "SERVICE_ACCOUNT": 1 } }`. This endpoint MUST require `platform.users.read`
permission.

#### Scenario: Operator fetches summary counts

- GIVEN an authenticated operator with `platform.users.read`
- WHEN `GET /api/admin/users/summary` is invoked
- THEN the system SHALL return `200 OK` with counts grouped by status and principalType

#### Scenario: Insufficient permission returns 403

- GIVEN a principal without `platform.users.read`
- WHEN `GET /api/admin/users/summary` is invoked
- THEN the system SHALL return `403 Forbidden`

---

### Requirement: Vitest specs for UsersView and UserDetailView

Vitest suites MUST cover UsersView component rendering with filter interactions and summary card
display, UserDetailView rendering with all section data, optimistic lock conflict handling in
row actions, and toast notifications on action success or 409 refresh.

#### Scenario: UsersView renders filter bar and summary card

- GIVEN UsersView mounted with mock API returning summary and list data
- WHEN the component renders
- THEN Vitest SHALL verify the filter bar, summary card, and data rows are present

#### Scenario: UserDetailView renders all sections

- GIVEN UserDetailView mounted with mock API returning full user detail
- WHEN the component renders
- THEN Vitest SHALL verify profile, consent, authentication methods, roles, and memberships sections

#### Scenario: Row action 409 triggers toast and refresh

- GIVEN UsersView with a row action pending
- WHEN the action returns `409 Conflict`
- THEN Vitest SHALL verify a toast is shown and the list refresh is triggered

---

### Requirement: BDD scenarios for user list, detail, deactivate, and reactivate

Cucumber feature files MUST cover the full user management flow: list rendering with filters,
detail navigation, deactivate with optimistic lock, reactivate with optimistic lock, and
permission denial scenarios.

#### Scenario: List users with all filters applied

- GIVEN an authenticated operator with `platform.users.read`
- WHEN the operator applies status, principalType, date-range, and email filters
- THEN the system SHALL return filtered results matching all criteria

#### Scenario: Deactivate user with correct version

- GIVEN an authenticated operator with `platform.users.deactivate`
- WHEN the operator deactivates a user with the correct `If-Match` version
- THEN the user status SHALL be INACTIVE
- AND a `USER_DEACTIVATED` audit event SHALL be recorded

#### Scenario: Reactivate user with correct version

- GIVEN an authenticated operator with `platform.users.reactivate`
- WHEN the operator reactivates a user with the correct `If-Match` version
- THEN the user status SHALL be ACTIVE
- AND a `USER_REACTIVATED` audit event SHALL be recorded

#### Scenario: Deactivation without permission returns 403

- GIVEN a principal without `platform.users.deactivate`
- WHEN the operator attempts to deactivate a user
- THEN the system SHALL return `403 Forbidden`

#### Scenario: Reactivation without permission returns 403

- GIVEN a principal without `platform.users.reactivate`
- WHEN the operator attempts to reactivate a user
- THEN the system SHALL return `403 Forbidden`
