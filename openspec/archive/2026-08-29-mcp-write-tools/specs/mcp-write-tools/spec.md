# mcp-write-tools Specification

## Purpose

Add five write tools to the existing MCP server so agents can drive the publication
lifecycle end-to-end while reusing the existing REST commands through `Mediator`. This
specification is gated on ADR-0019 (`openspec/changes/mcp-write-tools/adr-0019-mcp-write-tools.md`)
for enqueue vs publish semantics, failure surfacing, idempotency, and authorization; each
requirement below cites the ADR section it depends on.

## Requirements

### Requirement: Enqueue acknowledgement semantics (ADR-0019 §Q1)

Each of the five write tools MUST return the `PublicationResult` already returned by the
underlying REST handler: a synchronous acknowledgement carrying `publicationId`,
`status`, `scheduleMode`, `scheduledFor`, `nextSlotAfter`, `socialAccountId`,
`priority`, `title`, `bodyText`, `assetIds`, `externalPublicationId`, `publicUrl`,
`publishedAt`, plus a `correlationId`. The MCP tool MUST NOT block on the LinkedIn
publish; the worker is the boundary of that asynchronous step.

#### Scenario: create_publication returns the enqueue acknowledgement

- GIVEN an authenticated agent invokes `create_publication` with valid inputs and the
  write scope
- WHEN the underlying `CreatePublicationCommand` returns a `PublicationResult`
- THEN the MCP tool MUST return the same `PublicationResult` with an added
  `correlationId` field.

### Requirement: Failure surfacing via list_publications (ADR-0019 §Q2)

After enqueue, the publication record is persisted before the handler returns; a missed
`tools/call` response does not lose the publication. Agents MUST be able to discover a
FAILED, BLOCKED, or CANCELLED outcome through `list_publications(status=...)`. The MCP
read tools MUST therefore expose the same `status` filter values as the REST
`ListPublicationsQuery`.

#### Scenario: Agent recovers a missed write via list_publications

- GIVEN an agent invoked `create_publication` and missed the `tools/call` response
- WHEN the agent then calls `list_publications(status=FAILED)` over a range that
  contains the publication
- THEN the response MUST include the publication with its `failedAt`,
  `lastErrorCode`, and `lastErrorMessage`
- AND the agent MUST be able to call `retry_publication` against the returned
  `publicationId`.

### Requirement: Client-supplied idempotency key (ADR-0019 §Q3)

Each of the five write tools MUST accept an optional `idempotencyKey: String` parameter
(opaque, 1-128 chars). When the agent supplies the same key twice in the same workspace,
the second invocation MUST return the cached `PublicationResult` without creating or
modifying a record. The MCP adapter layer (NOT the publish handler) MUST consult a new
`idempotency_records` table keyed by `(workspaceId, principalId, toolName, key_hash)`
before dispatching; on collision it MUST return the stored response payload. The
plaintext key MUST NEVER be persisted.

#### Scenario: Idempotent create returns the same publication id

- GIVEN an agent invokes `create_publication` with `idempotencyKey = "agent-retry-1"`
- AND the handler returns `publicationId = pub-X`
- WHEN the agent invokes `create_publication` again with the same key in the same
  workspace
- THEN the MCP tool MUST return `publicationId = pub-X`
- AND the underlying `Mediator.send(CreatePublicationCommand)` MUST NOT run a second
  time.

#### Scenario: Idempotency key is scoped to workspace and principal

- GIVEN an agent in workspace A invokes `create_publication` with `idempotencyKey =
  "k1"`
- WHEN an agent in workspace B invokes `create_publication` with `idempotencyKey =
  "k1"`
- THEN workspace B's invocation MUST create a new publication
- AND the cached response MUST NOT be reused across workspaces.

### Requirement: Authorization and per-tool hints (ADR-0019 §Q4)

The five write tools MUST require `mcp:publications:write` and MUST advertise the
following JSON schema hints:

- `destructiveHint = true` for `create_publication`, `edit_publication`,
  `delete_publication`, and `retry_publication`
- `idempotentHint = true` for `cancel_publication` and `delete_publication`
- `openWorldHint = true` for all five (LinkedIn is an external system)

#### Scenario: Schema announcement carries per-tool hints

- GIVEN the MCP server is started and `tools/list` is invoked
- WHEN the response is rendered
- THEN each write tool MUST include the hints above in its `annotations`
- AND the response MUST advertise exactly nine tools (5 reads + `mcp_ping` + 3 from PR 2,
  later 9 once PR 3 lands).

### Requirement: Validation, audit, and rate limiting per call

Each write tool MUST validate the input contract before dispatch (channel connection,
schedule validity, body length), MUST emit a `McpToolInvocationAuditFact` with the
returned `publicationId`, MUST decrement the `mcp-publications-write` rate-limit
bucket, and MUST map infrastructure errors through `McpErrorMapper` so consumers see the
documented categories (`authorization`, `validation`, `platform`, `idempotency`,
`rate_limit`).

#### Scenario: create_publication covers the full contract

- GIVEN a token with `mcp:publications:write` and a valid `socialAccountId`,
  `scheduleMode = SCHEDULED_AT`, `scheduledFor`, and an optional `idempotencyKey`
- WHEN `create_publication` runs
- THEN the audit fact MUST include `publicationId`
- AND the response MUST carry `correlationId`
- AND the rate-limit bucket for write calls MUST be decremented exactly once
- AND the response MUST be `ToolResponse.success(PublicationResult)` so the agent
  observes success.
