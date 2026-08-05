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
