# Delta for publishing

## ADDED Requirements

### Requirement: Update-Only Publication Misses Return HTTP 404

The system MUST translate current-workspace publication misses for update-only publishing operations
into HTTP 404 at the HTTP boundary.

Any endpoint that intentionally scopes publication lookup by the caller's current workspace and
throws `PublicationNotFoundException` for a miss MUST expose that miss as not found rather than an
internal server error. This contract applies only to update-only operations and MUST NOT redefine
create/save flows that are allowed to create a draft when no current-workspace row exists.

#### Scenario: Edit request misses the current-workspace publication

- GIVEN `PATCH /api/publishing/publications/{publicationId}` is an update-only operation
- AND the current workspace has no matching publication row for `publicationId`
- WHEN the HTTP request reaches the publishing boundary
- THEN the system MUST return HTTP 404
- AND the response MUST NOT degrade to HTTP 500

#### Scenario: Sibling update-only operations share the same not-found contract

- GIVEN delete, cancel, retry, or reschedule uses the same current-workspace publication lookup
  semantics
- AND the operation intentionally treats cross-workspace targets as not found
- WHEN no matching publication exists in the current workspace
- THEN the system MUST return HTTP 404 for that endpoint
- AND it MUST leave rows in other workspaces unchanged

#### Scenario: Create-capable save flows remain out of scope

- GIVEN a publishing flow is explicitly allowed to create a draft when the current workspace has no
  matching row
- WHEN that flow evaluates a missing current-workspace target
- THEN this requirement MUST NOT force HTTP 404
- AND the flow MUST continue to follow its create/save contract
