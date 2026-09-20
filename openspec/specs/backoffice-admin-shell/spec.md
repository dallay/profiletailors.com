# Backoffice Admin Shell Specification

## Purpose

Admin SPA shell (`apps/web/admin`, root `/` under `AdminLayout`): layout, permission-filtered nav
registry, routing seams, inert placeholders. No dashboard-app change.

## Requirements

### Requirement: Shell Loads for Authorized Admin

The system MUST render `AdminLayout` with sidebar nav for any authenticated principal holding
platform access.

#### Scenario: Authorized admin opens shell

- GIVEN an authenticated admin with platform access
- WHEN opening the admin shell root
- THEN layout and nav render without redirect

#### Scenario: Unauthorized principal is denied

- GIVEN a principal without platform access
- WHEN opening the shell root
- THEN the system redirects to login or access-denied

### Requirement: Registry-Driven Navigation

The system MUST render nav exclusively from a central registry
`{ key, route, permission, status: live|planned }`, filtered by `hasPermission`.

#### Scenario: Nav filtered by permission

- GIVEN a principal lacking a registry entry's permission
- WHEN the shell renders nav
- THEN that entry is hidden

#### Scenario: Extensibility is one entry

- GIVEN a new future area with route and permission
- WHEN one registry entry is added
- THEN nav, route, and gating work with no layout change

### Requirement: Direct-Invitations Nav Entry

The system MUST list `direct-invitations` in nav when the principal holds
`platform.invitations.read`.

#### Scenario: Permitted principal sees direct-invitations

- GIVEN a principal with `platform.invitations.read`
- WHEN the shell renders nav
- THEN `direct-invitations` is visible and routes to the existing view

### Requirement: Inert Planned-Area Placeholders

The system MUST render planned areas
(overview/users/waitlist/invitations/notifications/governance/configuration/audit) via a shared
static view that is explicit "planned", permission-gated, and performs zero fetch.

#### Scenario: Planned area shows planned state

- GIVEN a permitted principal opening a planned area
- WHEN the route renders
- THEN an explicit planned message shows and no API request is issued

#### Scenario: Unpermitted planned area stays hidden

- GIVEN a principal lacking the area's permission
- WHEN the shell renders nav and routes
- THEN the entry is hidden and direct navigation is denied

### Requirement: Localized Labels

The system MUST provide EN/ES nav and placeholder labels for every registry entry.

#### Scenario: Spanish labels render

- GIVEN locale `es`
- WHEN the shell renders nav
- THEN all entries show Spanish labels

## Acceptance Mapping

| # | Criterion                                                 | Scenario                                   |
|---|-----------------------------------------------------------|--------------------------------------------|
| 1 | Shell loads + nav visible for authorized admin            | Shell Loads / Authorized admin opens shell |
| 2 | `direct-invitations` in nav                               | Direct-Invitations Nav Entry               |
| 3 | Planned areas show planned state, zero fetch              | Inert Planned-Area Placeholders            |
| 4 | Gating matches server; `admin-check` + `admin-build` pass | Registry-Driven Navigation + authz delta   |

### Requirement: Direct-invitations list section

`DirectInvitationsView` MUST render a list section above/below the create form: table of
direct-shaped rows, status filter, email search, pagination, and loading/empty/error states. Labels
MUST have EN+ES keys. Row resend/revoke MUST reuse existing endpoints with `expectedVersion`. The
view MUST NOT render token material. List fetch MUST require `platform.invitations.read`; without it
the view MUST show access-denied and issue zero list requests.

#### Scenario: Table renders seeded rows

- GIVEN a permitted operator and a populated list response
- WHEN the view loads
- THEN rows show email, target, status, expiry with resend/revoke actions

#### Scenario: Filters drive list query

- GIVEN a permitted operator
- WHEN status filter or email search changes
- THEN the view re-queries `GET /direct` with updated params reset to page 0

#### Scenario: Empty, loading, and error states

- GIVEN a permitted operator
- WHEN the list is loading, empty, or failed
- THEN the view shows the matching state message and no stale rows

#### Scenario: Row actions reuse existing endpoints

- GIVEN a rendered ACTIVE row
- WHEN resend or revoke is confirmed
- THEN the existing endpoint runs and the list refreshes with the bumped version

#### Scenario: Spanish labels render

- GIVEN locale `es`
- WHEN the list section renders
- THEN filters, headers, pagination, and empty-state copy show Spanish labels

## Acceptance Mapping

| # | Criterion                                 | Scenario                                                                     |
|---|-------------------------------------------|------------------------------------------------------------------------------|
| 1 | Paged list, `issuedAt desc`, no token     | invitations: Authorized operator lists                                       |
| 2 | Pagination + status/email filters         | invitations: Pagination; Status and email filters                            |
| 3 | 401/403 on list                           | invitations: Unauthenticated; Unpermitted                                    |
| 4 | Table + filters + states + EN/ES          | backoffice-admin-shell: Table renders; Filters; Empty/loading/error; Spanish |
| 5 | Row resend/revoke from existing endpoints | backoffice-admin-shell: Row actions reuse                                    |
| 6 | Access-denied when no permission          | backoffice-admin-shell: No permission                                        |

### Requirement: Direct-invitations list section

`DirectInvitationsView` MUST render a list section above/below the create form: table of
direct-shaped rows, status filter, email search, pagination, and loading/empty/error states. Labels
MUST have EN+ES keys. Row resend/revoke MUST reuse existing endpoints with `expectedVersion`. The
view MUST NOT render token material. List fetch MUST require `platform.invitations.read`; without it
the view MUST show access-denied and issue zero list requests.

#### Scenario: Table renders seeded rows

- GIVEN a permitted operator and a populated list response
- WHEN the view loads
- THEN rows show email, target, status, expiry with resend/revoke actions

#### Scenario: Filters drive list query

- GIVEN a permitted operator
- WHEN status filter or email search changes
- THEN the view re-queries `GET /direct` with updated params reset to page 0

#### Scenario: Empty, loading, and error states

- GIVEN a permitted operator
- WHEN the list is loading, empty, or failed
- THEN the view shows the matching state message and no stale rows

#### Scenario: Row actions reuse existing endpoints

- GIVEN a rendered ACTIVE row
- WHEN resend or revoke is confirmed
- THEN the existing endpoint runs and the list refreshes with the bumped version

#### Scenario: Spanish labels render

- GIVEN locale `es`
- WHEN the list section renders
- THEN filters, headers, pagination, and empty-state copy show Spanish labels

## Acceptance Mapping

| # | Criterion                             | Scenario                                                                     |
|---|---------------------------------------|------------------------------------------------------------------------------|
| 1 | Paged list, `issuedAt desc`, no token | invitations: Authorized operator lists                                       |
| 2 | Pagination + status/email filters     | invitations: Pagination; Status and email filters                            |
| 3 | 401/403 on list                       | invitations: Unauthenticated; Unpermitted                                    |
| 4 | Table + filters + states + EN/ES      | backoffice-admin-shell: Table renders; Filters; Empty/loading/error; Spanish |
| 5 | Row resend/revoke from list           | backoffice-admin-shell: Row actions                                          |
