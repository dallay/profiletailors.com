# Delta: Direct-Invitations Collection Read

## ADDED Requirements

### Requirement: Paged direct-invitations list

The system MUST expose `GET /api/admin/invitations/direct` returning a paged list of `source=DIRECT` invitations sorted `issuedAt desc`. Query params MUST be `page`, `size`, optional `status` and `email`. Rows MUST be direct-shaped (`invitationId, email, target, workspaceId, status, expiresAt, version`) and MUST NOT contain token material. The endpoint MUST require `INVITATIONS_READ`. `status` filter MUST accept canonical statuses; `email` filter MUST match normalized email substring case-insensitively.

#### Scenario: Authorized operator lists direct invitations

- GIVEN an operator with `platform.invitations.read` and seeded DIRECT rows
- WHEN `GET /direct?page=0&size=20` is received
- THEN HTTP 200 returns a page ordered `issuedAt desc` with direct-shaped rows and no token field

#### Scenario: Pagination is honored

- GIVEN 25 DIRECT rows exist
- WHEN `GET /direct?page=1&size=10` is received
- THEN the second page of 10 rows is returned with correct total count

#### Scenario: Status and email filters combine

- GIVEN DIRECT rows with mixed statuses and emails
- WHEN `GET /direct?status=ACTIVE&email=ops@` is received
- THEN only ACTIVE rows whose normalized email contains `ops@` are returned

#### Scenario: Unauthenticated list is rejected

- GIVEN no credentials
- WHEN `GET /direct` is received
- THEN HTTP 401 is returned

#### Scenario: Unpermitted list is forbidden

- GIVEN a principal without `platform.invitations.read`
- WHEN `GET /direct` is received
- THEN HTTP 403 is returned

#### Scenario: Empty list returns empty page

- GIVEN no DIRECT rows match the filters
- WHEN `GET /direct?status=REVOKED` is received
- THEN HTTP 200 returns an empty items array with total zero

## REMOVED Requirements

(None — collection read is additive; lifecycle and token rules unchanged.)
