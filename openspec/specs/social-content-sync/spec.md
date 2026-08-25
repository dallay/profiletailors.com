# Social Content Sync Specification

## Purpose

Define workspace-scoped discovery and bounded, read-only synchronization of LinkedIn Company Page posts.

## Requirements

### Requirement: Versioned Social-Content Read Contracts

The system MUST expose version-1 contracts through `POST /api/publishing/social-content/sync`, `GET /api/publishing/social-content/calendar`, and `GET /api/publishing/social-content/posts/{externalPostId}`. Each request MUST require `Authorization: Bearer ...`, `X-Workspace-Id`, and `Accept: application/vnd.api.v1+json`; sync MUST also require JSON `Content-Type`. Sync MUST accept `{ "actorId": "..." }` and return `actorId`, `importedCount`, `highWaterMark`, and `status`. Calendar MUST require ISO-8601 `from`/`to`, MAY accept `actorId`, `lifecycle`, opaque `cursor`, and `limit` (1–100), and MUST return `items` plus `nextCursor`; detail MUST return the post read model. Responses MUST contain no credentials, and invalid input/context MUST use the existing problem-details contract.

#### Scenario: Valid sync and calendar request
- GIVEN an authenticated member and workspace context
- WHEN the member calls the version-1 sync or calendar contract with valid headers and parameters
- THEN the request MUST reach the corresponding Mediator command/query
- AND the response MUST contain only workspace-scoped read data

#### Scenario: Missing contract headers is rejected
- GIVEN a request lacks a Bearer token or `X-Workspace-Id`
- WHEN any social-content endpoint is called
- THEN the system MUST reject it
- AND the provider and application use case MUST NOT execute

### Requirement: Workspace-Isolated Mediator Wiring

Spring MUST register the sync command, calendar query, and post-detail query handlers. Controllers MUST dispatch through `Mediator`; handlers MUST derive `WorkspaceScope` from `ResourceContextProvider` and resolve actors/posts using workspace-qualified repository operations. A record from another workspace MUST never be returned or used in a provider call.

#### Scenario: Foreign post is inaccessible
- GIVEN post `p-1` belongs to workspace B
- WHEN a member of workspace A requests `p-1`
- THEN the endpoint MUST return the typed not-found or isolation problem
- AND workspace B data MUST remain unchanged

### Requirement: Bounded Cursor Pagination

Calendar and provider reads MUST preserve an opaque cursor and return `nextCursor`, or `null` at the end. The sync process MUST bound provider pages and persist its checkpoint only after the complete bounded batch succeeds. An invalid cursor, reversed range, or limit outside 1–100 MUST be rejected without a provider call.

#### Scenario: Cursor continues without duplication
- GIVEN a calendar page returns `nextCursor = c-2`
- WHEN the client requests the next page with `cursor=c-2`
- THEN the reader MUST continue from that cursor
- AND the response MUST not duplicate records from the prior page

### Requirement: Idempotent Checkpointed Sync

Sync MUST upsert by workspace/provider/actor/external-post identity. It MUST resume from the stored cursor, request an overlap before the stored high-water mark, deduplicate overlap results, and advance `highWaterMark` and `lastSuccessfulAt` only after persistence succeeds. Rate-limit retries MUST be bounded and use `Retry-After` when supplied; failures MUST leave the prior checkpoint intact.

#### Scenario: Retry and checkpoint are safe
- GIVEN a rate-limited provider followed by a successful bounded page
- WHEN sync retries and persists the page
- THEN the post MUST be stored once
- AND the checkpoint MUST advance only after persistence completes

### Requirement: Retention and Tombstones

Imported activity MUST receive the configured retention expiry. A completed full sync MAY tombstone previously stored posts absent from the complete provider result; incremental pages MUST NOT infer deletion from an incomplete page. Tombstoned posts MUST remain workspace-scoped, non-mutable, and excluded from active results while their minimal identity is retained according to the retention policy.

#### Scenario: Missing post becomes a tombstone
- GIVEN a completed full sync no longer contains an earlier post
- WHEN reconciliation completes
- THEN that post MUST be represented as `TOMBSTONED`
- AND it MUST NOT be returned as active or become mutable

### Requirement: Executable Cucumber Coverage

The PR MUST provide tagged executable scenarios for contract headers, workspace isolation, cursor paging, successful sync, retry/checkpoint behavior, tombstones, and safe-off denial. Features MUST carry the social-content domain tag plus `@smoke` and `@fast`, and MUST use deterministic provider fakes.

#### Scenario: BDD proves default denial
- GIVEN the default feature gates are disabled
- WHEN a tagged Cucumber scenario calls sync
- THEN the scenario MUST observe a typed denial
- AND the fake provider MUST record zero calls

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
