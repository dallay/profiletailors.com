# Delta: Direct-Invitations List Section

## ADDED Requirements

### Requirement: Direct-invitations list section

`DirectInvitationsView` MUST render a list section above/below the create form: table of direct-shaped rows, status filter, email search, pagination, and loading/empty/error states. Labels MUST have EN+ES keys. Row resend/revoke MUST reuse existing endpoints with `expectedVersion`. The view MUST NOT render token material. List fetch MUST require `platform.invitations.read`; without it the view MUST show access-denied and issue zero list requests.

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

| # | Criterion | Scenario |
|---|-----------|----------|
| 1 | Paged list, `issuedAt desc`, no token | invitations: Authorized operator lists |
| 2 | Pagination + status/email filters | invitations: Pagination; Status and email filters |
| 3 | 401/403 on list | invitations: Unauthenticated; Unpermitted |
| 4 | Table + filters + states + EN/ES | backoffice-admin-shell: Table renders; Filters; Empty/loading/error; Spanish |
| 5 | Row resend/revoke from table | backoffice-admin-shell: Row actions reuse |
