# Delta for Social Content Sync — Production R2DBC Calendar Cursor Continuation

## ADDED Requirements

### Requirement: Calendar Pagination Applies Cursor Predicate in Production R2DBC

The production calendar reader MUST decode the opaque cursor and apply its keyset predicate before returning the next page. Opaque cursors MUST NOT be honored for calendar pagination outside the production reader path.

#### Scenario: Continuation via production reader
- GIVEN a prior page returned a non-null `nextCursor`
- WHEN the next request carries that cursor
- THEN the production reader MUST return the next stable page with no overlap

#### Scenario: First page without cursor
- GIVEN no cursor was supplied
- WHEN the calendar request is made
- THEN the production reader MUST return the earliest deterministic-tuple-ordered page

### Requirement: Deterministic Keyset Ordering Across the Full Post Identity

The production reader MUST order results by `(published_at, provider, social_account_id, external_post_id) ASC` and MUST apply a keyset predicate over those same four fields. It MUST apply `WHERE workspace_id = :requestWorkspaceId` as the leading predicate. The opaque cursor envelope MUST additionally carry `v` and `workspaceId` as version and provenance metadata. Effective tenant scope MUST come exclusively from authenticated request context, and SQL MUST remain scoped by that request workspace.

#### Scenario: Tie on published_at keeps pages stable
- GIVEN multiple posts share the same `published_at`
- WHEN the calendar request is paginated
- THEN ordering MUST remain stable across pages with no overlap

#### Scenario: Workspace isolation in the keyset result
- GIVEN a decoded cursor from a prior page
- WHEN the resulting query runs
- THEN no row from any other workspace MUST be returned

### Requirement: Opaque, Bounded nextCursor with Strict Validation

The cursor envelope MUST contain the six fields `(v, workspaceId, publishedAt, provider, socialAccountId, externalPostId)`: `v` is version metadata, `workspaceId` is provenance metadata, and `(publishedAt, provider, socialAccountId, externalPostId)` is the four-field keyset tuple. The envelope MUST be encoded as URL-safe Base64 without padding. The system MUST emit `nextCursor` only when another page exists, MUST omit it on the final page, and MUST bind each emitted cursor to the identity tuple of the last row returned. The reader MUST compare the decoded `workspaceId` with the authenticated request-context workspace and MUST reject mismatches. A cursor that cannot be decoded, has an unsupported version, is malformed, or has a workspace mismatch MUST yield HTTP 400 Problem Details with error code `INVALID_SOCIAL_CONTENT_CURSOR`.

#### Scenario: Mid-sequence cursor is bounded
- GIVEN a request whose result set is not the final page
- WHEN the response is built
- THEN the response MUST carry a non-null `nextCursor` whose decoded identity equals the last row's tuple

#### Scenario: Final page omits nextCursor
- GIVEN the first request whose result set is smaller than the requested limit
- WHEN the response is built
- THEN the response MUST carry `nextCursor = null`

#### Scenario: Reject a cursor issued for another workspace
Given a cursor was issued while operating in workspace "WS-A"
When that cursor is used while operating in workspace "WS-B"
Then the response status should be 400
And the error code should be "INVALID_SOCIAL_CONTENT_CURSOR"

#### Scenario: Cursor contents cannot override workspace isolation
Given the request operates in workspace "WS-B"
And a crafted cursor contains keyset values originating from workspace "WS-A"
When the page is requested
Then no content belonging to workspace "WS-A" should be returned
And repository access should remain scoped to workspace "WS-B"

#### Scenario: Reject a malformed cursor
When a malformed social content cursor is supplied
Then the response status should be 400
And the error code should be "INVALID_SOCIAL_CONTENT_CURSOR"

#### Scenario: Calendar cursor excludes sync checkpoints
- GIVEN a sync checkpoint cursor from the sync checkpoint port
- WHEN the calendar reader receives it
- THEN the calendar codec MUST NOT decode it
- AND the calendar keyset predicate MUST NOT apply to that port

### Requirement: No Duplicates or Omissions Across Pages Under Stable Snapshot

Against an unchanged dataset, paginating with valid cursors MUST NOT return duplicates or omissions across page boundaries. The system MUST fetch `limit + 1` rows to detect a next page, drop the extra row from the response, and use its identity to build `nextCursor`.

#### Scenario: Boundary overlap is exact
- GIVEN a dataset of 3 rows and `limit = 2`
- WHEN the client paginates both pages
- THEN both pages together MUST contain those 3 rows exactly once
- AND the second page MUST carry `nextCursor = null`

#### Scenario: Guarantee scope is bounded to a stable snapshot
- GIVEN a single client's paginated sequence against an unchanged dataset
- WHEN the sequence is replayed with the returned cursors
- THEN the system MUST NOT return duplicates or omissions
- AND the guarantee MUST be modulo concurrent rewrites of `published_at`

### Requirement: Workspace binding in cursor is provenance only

The cursor's embedded `workspaceId` MUST be treated solely as a provenance and binding attribute used to reject foreign-workspace cursors, not as an authorization source. The repository MUST derive the effective workspace for tenant isolation exclusively from the authenticated request context and MUST NOT use the cursor's `workspaceId` to construct the tenant scope.

#### Scenario: Cursor workspace cannot authorize
- GIVEN a request context with workspace "WS-B"
- AND a cursor whose decoded `workspaceId` is "WS-A"
- WHEN the page is requested
- THEN the repository's SQL `WHERE` clause uses workspace "WS-B"
- AND the cursor is rejected with 400 BEFORE the repository executes the query
