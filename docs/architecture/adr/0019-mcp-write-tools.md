# ADR-0019: MCP Write Tools for Publication Lifecycle

- Status: Accepted
- Date: 2026-08-28
- Decision owners: Principal Architect
- Scope: `server/smp/src/main/kotlin/com/profiletailors/smp/mcp/`,
  `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/`,
  `openspec/specs/mcp-server/`, `openspec/specs/publishing/`,
  `openspec/specs/iam/`, Keycloak realm configuration for MCP clients.
- Supersedes: None
- Superseded by: None
- Related:
  - [ADR-0002: Adhere to Hexagonal Architecture](0002-adhere-to-hexagonal-architecture.md)
  - [ADR-0008: Application-Level Multi-tenancy](0008-application-level-multi-tenancy.md)
  - OpenSpec: `openspec/changes/mcp-write-tools/` (this change)
  - OpenSpec: `openspec/changes/mcp-server/` (DALLAY-434, read path; closed but the read path was never wired — see §Context)
  - C4: `docs/architecture/c4/` (MCP resource server, publishing bounded context)
  - Issues: Linear DALLAY-590 (this ADR's gate), DALLAY-416 (consumer, in design)

## Context

DALLAY-434 shipped four MCP read tools (`list_publications`, `get_calendar`,
`list_channels`, `list_providers`) whose BDD scenarios asserted only that the
JSON-RPC transport answered `200`, never the tool payload. The tool classes
exist but are plain Kotlin, not `@Component` or `@McpTool`; no
`ToolCallbackProvider` bean is registered; the audit fact has no publisher;
the workspace-membership checker returns `Mono.just(true)`; the
authorizer accepts any `mcp:*` scope. An agent hitting `/api/mcp` today sees
zero tools in `tools/list`. This ADR cannot proceed without DALLAY-590's
Phase 0 (PR 0) wiring those prerequisites — that work is the explicit
gate.

DALLAY-590 must add five write tools that delegate to the existing REST
commands (`CreatePublicationCommand`, `EditPublicationCommand`,
`CancelPublicationCommand`, `DeletePublicationCommand`,
`RetryPublicationCommand`). The five handlers are synchronous and complete
after a single transactional write + job enqueue (`exploration.md` §1.2);
they do not block on the LinkedIn boundary. `PublicationResult` carries
`publicationId`, `status` (`DRAFT|QUEUED|SCHEDULED`), and scheduling fields
but not `failedAt`/`lastErrorCode`/`blockedReason` — those live only on
`PublicationDraft` and `ListPublicationItem`. There is no idempotency key
on any publish command today.

This ADR answers the four blocking questions: enqueue vs publish semantics,
failure surfacing, idempotency, and authorization.

## Decision drivers

- **Agent reliability**: agents retry. The contract must let an agent
  safely recover from a dropped `tools/call` response.
- **Surface parity with REST**: the SPA already uses these five REST
  endpoints; the MCP write tools should expose the same business capability,
  not invent a new one.
- **No new async surface**: the existing publishing pipeline is async via
  the `PublishingWorker` poll loop. The MCP tool contract should reuse that
  and not introduce a second async surface that agents must learn.
- **Workspace isolation and audit parity with the read path**: every write
  call MUST be auditable with the same fidelity as the read path promises.
- **Scope simplicity**: one new scope (`mcp:publications:write`) is
  preferable to a matrix of read/write per entity; agents that can write
  should also be able to read their own work.
- **Bounded-context respect**: the write tools must remain thin adapters
  over the existing `Mediator` + handlers. Domain rules, policies, and
  persistence are not touched by this ADR.

## Decision

### Q1 — Enqueue-ack semantics (NOT synchronous publish, NOT async task ID)

**Context.** All five publish handlers are synchronous and complete after a
single transactional write + job enqueue. The REST controllers await
`mediator.send(command)` and return `PublicationResult`. There is no async
task ID, no `Location` header, no callback URL. The LinkedIn publish
happens asynchronously in the worker after the request returns.

**Decision.** The five write tools MUST return the `PublicationResult`
that the underlying REST handler returns today — an enqueue acknowledgement
carrying `publicationId`, `status` (`DRAFT|QUEUED|SCHEDULED`), `scheduleMode`,
`scheduledFor`, `nextSlotAfter`, `socialAccountId`, `priority`, `title`,
`bodyText`, `assetIds`, `externalPublicationId`, `publicUrl`, `publishedAt`.
`create_publication` MUST NOT block until the platform boundary accepts or
rejects. `cancel_publication`, `delete_publication`, and `retry_publication`
likewise return after the synchronous state transition. The result MUST
also carry a `correlationId` (the `McpErrorMapper`-generated UUID) that the
agent can use to reconcile its retry against the audit log.

**Consequences.**

- *Positive* — reuses the existing publishing pipeline unchanged; matches
  REST surface parity; no new async surface for agents to learn.
- *Negative* — agents that want immediate delivery confirmation must poll.
- *Neutral* — the contract is identical to a successful REST `200 OK`.

**Reversibility.** High. Switching to synchronous publish is a per-tool
behaviour change confined to the MCP adapter layer; the underlying
`Mediator` and handlers stay untouched.

### Q2 — Failure surfacing via existing read tools

**Context.** After enqueue, the publication record is persisted before the
handler returns, so a missed `tools/call` response does not lose the
publication. `list_publications` already exposes `status`, `failedAt`,
`lastErrorCode`, `lastErrorMessage`, `blockedReason`, `retryCount`
(`PublishingApi.kt:210-227`). There is no push mechanism from the worker
back to the agent; the only side-effects of a worker transition are a row
update on `publications`, a row insert on `notification_events`, and an
SSE event on `ChannelEventStreamRegistry` that covers only
`CONNECTED_CHANNEL_*` transitions — not `PUBLICATION_FAILED` /
`PUBLICATION_PUBLISHED`.

**Decision.** `list_publications` MUST be sufficient for an agent that
missed the original `tools/call` response. The MCP tool contract MUST add
`list_publications` filters for `status = BLOCKED|FAILED|CANCELLED` so
agents can scan for their prior writes without retrieving the full
calendar. A dedicated event subscription / webhook is out of scope for
DALLAY-590.

**Consequences.**

- *Positive* — no new read tool; no stateful streaming; agents already
  know how to use `list_publications`.
- *Negative* — agents incur poll latency to learn terminal state; a
  dedicated `get_publication(publicationId)` tool is not added.
- *Neutral* — SSE channel events remain dashboard-targeted.

**Reversibility.** Medium. Adding `get_publication` later is additive and
does not break existing consumers; revisiting the polling assumption is a
follow-up ADR.

### Q3 — Idempotency via client-supplied idempotency key

**Context.** There is no idempotency key on any publish command today.
A retried `create_publication` creates a fresh `pub-${UUID.randomUUID()}`
on every invocation (`CreatePublicationHandler.kt:89`), so a dropped
`tools/call` followed by a retry produces two distinct publications with
identical content. The `IdempotencyKey` value class
(`SocialContentModels.kt:52`) is the closest existing model on the
social-content reply path.

**Decision.** `create_publication`, `edit_publication`,
`cancel_publication`, `delete_publication`, and `retry_publication` MUST
accept an optional `idempotencyKey: String` parameter (1-128 chars, opaque).
When the agent supplies the same key twice in the same workspace, the
second invocation MUST return the same `PublicationResult` as the first
without creating or modifying a new record. `idempotencyKey` MUST be scoped
to `(workspaceId, principalId, toolName)` and hashed (SHA-256) before
storage; the plaintext is never persisted. A new `idempotency_records`
table MUST be added with columns
`(workspace_id, principal_id, tool_name, key_hash, response_json,
created_at)` plus a unique constraint on
`(workspace_id, principal_id, tool_name, key_hash)`. The mediator dispatch
in the MCP adapter layer (NOT in the publish handler) MUST check the
table before invoking the handler; on collision it MUST return the stored
`response_json`. The pre-existing `IdempotencyKey` value class SHOULD be
promoted to a shared-kernel type under `common/domain/` and reused.
REST controllers MUST NOT consume the `idempotencyKey` field for the write
tools; the field is MCP-only for now.

**Consequences.**

- *Positive* — safe agent retries; minimal new surface area (one table,
  one column); reuses existing value-object precedent.
- *Negative* — adds one write per tool call to `idempotency_records`;
  introduces a new error code (`idempotency_conflict`) for malformed
  collisions; five write tools gain an optional parameter that must be
  documented.
- *Neutral* — REST keeps its current contract; MCP-only idempotency is a
  deliberate scoping choice.

**Reversibility.** Medium. The table is additive; removing it requires a
Liquibase migration and removes the retry-safety guarantee for any agent
that adopted the contract. REST-side idempotency is a separate ADR.

### Q4 — Authorization: one new scope, tool-level enforcement

**Context.** `mcpToolInvocationAuthorizer` today accepts any `mcp:*` scope
(`McpToolInvocationAuthorizer.kt:22-24`). This is a placeholder; per-tool
enforcement is required before write tools can be safely exposed. The
realm-side scope declaration and protocol mapper already exist for the
two read scopes (`openspec/changes/mcp-server/spikes/SPIKE_OUTCOME.md:118-180`).
`ResourceMetadataController.scopesSupported` lists only the two read scopes.

**Decision.** `mcp:publications:write` MUST be added as a new MCP scope.
The scope MUST be declared in (1) the Keycloak realm on the MCP client
scope that issues MCP tokens; (2) `ResourceMetadataController.scopesSupported`;
(3) `McpToolMetadata.registry` — all 5 write tools map to the new scope.
The 5 write tools MUST advertise `destructiveHint = true` for
create/edit/delete/retry; `idempotentHint = true` for cancel and delete;
`openWorldHint = true` for all five (LinkedIn is an external system).
A token with `mcp:publications:write` MUST also have
`mcp:publications:read` to inspect its own work; this is a Keycloak
client-scope grant choice, not a code change.
`McpToolInvocationAuthorizer` MUST be replaced. The current permissive
check MUST be replaced with a strict lookup against
`McpToolMetadata.requiredScope(toolName)`. A missing or wrong scope MUST
return `ApplicationError(code = "insufficient_scope", category =
"authorization", retryable = false)`. `McpWorkspaceMembershipChecker` MUST
be replaced with a real query against the tenancy bounded context. The
current `Mono.just(true)` stub MUST NOT ship with write tools. No new
media-write scope; `create_publication` MAY reference existing asset IDs
via `assetIds`; uploading new media is NOT in scope for DALLAY-590.

**Consequences.**

- *Positive* — one new scope, one realm change; per-tool enforcement
  blocks the read-token-from-writing attack class; workspace membership is
  no longer a stub.
- *Negative* — five new error codes (`insufficient_scope`,
  `idempotency_conflict`, `publication_not_found`,
  `publication_state_conflict`, `publication_validation_failed`,
  `media_unavailable`) require consumer-side parsing; mitigated by the
  `category` field grouping them.
- *Neutral* — `@McpTool.McpAnnotations` exposes the new hint fields to
  clients without a contract change.

**Reversibility.** Medium. The scope declaration and realm config are
additive; removing them removes the write capability but not the read
path. `McpWorkspaceMembershipChecker` and `McpToolInvocationAuthorizer`
replacements are not reversible without re-introducing the stubs, which
must be justified in a new ADR.

### Prerequisite: fix the read-path wiring (carried over from exploration §0)

None of the four existing read tools are annotated with `@McpTool` or
registered with the Spring AI transport. Adding five write tools without
fixing this would result in a published MCP server where the new write
tools appear in `tools/list` but the four legacy read tools do not. This
change MUST include, as a prerequisite slice before the write tools:

1. `@McpTool` + `@McpToolParam` annotations on the four read methods in
   `tools/PublicationTools.kt`, `tools/ChannelTools.kt`,
   `tools/ProviderTools.kt`.
2. A `ToolCallbackProvider` bean in `infrastructure/McpConfiguration.kt`
   that picks up `@Component`-annotated classes carrying `@McpTool`
   methods.
3. `generateOutputSchema = true` on each read tool to publish the JSON
   schema.
4. Wire `McpToolInvocationAuditFact` emission on every tool call (read and
   write).

### Decision 5 — Audit emission and tool-call reconciliation

`McpToolInvocationAuditFact` MUST be emitted on every tool call (read and
write), with the following fields:

- existing: `toolName`, `scopeChecked`, `grantedScopes`, `workspaceId`,
  `correlationId`, `outcome` (`SUCCESS|DENIED|RATE_LIMITED|ERROR`).
- new: `publicationId: String?` — populated from the `PublicationResult`
  returned by the handler; `null` for tools that do not produce a
  publication (reads, media rejects).
- new: `clientToolCallId: String?` — populated from the MCP JSON-RPC
  request `id` if Spring AI surfaces it to the tool method; verify before
  relying on it.

The audit fact MUST be sent to a structured logger
(`org.slf4j.LoggerFactory.getLogger("mcp.audit")`) at INFO with the
`toMap()` payload rendered as JSON. The emit MUST happen in the MCP
adapter layer, after the handler returns and before the tool method
returns to Spring AI. A Kafka topic (`mcp.tool.invocations`) MAY be added
by observability later; for DALLAY-590, structured log is sufficient. The
`McpToolInvocationAuditFact` data class MUST be extended with backward
compatibility (constructor arguments with defaults).

## Scope and boundaries

Affected modules:

- `server/smp/src/main/kotlin/com/profiletailors/smp/mcp/` — wiring,
  adapter, audit, error mapping, rate limiting, scope enforcement.
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/` — no
  domain changes; only new `IdempotencyRecord` adapter.
- `openspec/specs/mcp-server/` — delta for catalog (5 new tools, 1 new
  scope).
- `openspec/specs/iam/` — delta for the new scope.
- `openspec/specs/publishing/` — delta for `idempotency_records`.
- Keycloak realm — declare `mcp:publications:write` on the MCP client
  scope.
- `docs/mcp-server.md` — update the tool catalog and onboarding notes.

Dependency rules (unchanged from DALLAY-434):

- `mcp` module depends on `publishing` module via Mediator.
- `mcp` module MUST NOT depend on `identity` (the auth chain already
  provides `McpAuthenticationToken` with `principal`).
- `publishing` module MUST NOT depend on `mcp`.
- The MCP adapter is the only place that calls `mediator.send(command)`
  for write commands; controllers do not change.

## Alternatives considered

### Q1 — Enqueue vs publish

- **(a) Enqueue ack (chosen)** — return `PublicationResult` after the
  transactional write + job enqueue, like the REST endpoint.
- (b) Synchronous publish — block until the LinkedIn boundary accepts or
  rejects. Couples the MCP timeout to the LinkedIn timeout (5-30s);
  invalidates Spring AI's STATELESS Streamable HTTP contract.
- (c) Async task ID — return `{ "taskId": "..." }` and require a separate
  `get_task` tool. Adds a second async surface.
- (d) Event subscription / webhook — requires stateful streaming.

Rejected (b), (c), (d): see §Decision and DALLAY-434 proposal §In Scope.

### Q2 — Failure surfacing

- (a) `list_publications` only (chosen).
- (b) Dedicated `get_publication(publicationId)` tool — no client today.
- (c) MCP `notifications` channel — requires stateful streaming.

Rejected (b) and (c) for the reasons above. Revisit if DALLAY-416 proves
polling is unacceptable.

### Q3 — Idempotency

- (a) Client-supplied `idempotencyKey` (chosen).
- (b) Server-supplied `publicationId` reuse — refactor of
  `CreatePublicationCommand`; confuses identity with intent.
- (c) No idempotency — violates agent reliability (Decision driver 1).
- (d) REST-side idempotency header — out of scope for DALLAY-590.

Rejected (b), (c), (d): see §Decision.

### Q4 — Authorization

- (a) One new scope `mcp:publications:write` (chosen).
- (b) Per-operation scopes — five new realms for marginal benefit.
- (c) No new scope, reuse `mcp:publications:read` — catastrophic for
  least-privilege.
- (d) New media-write scope `mcp:media:write` — out of scope.

Rejected (b), (c), (d): see §Decision.

## Consequences

### Positive

- Five new MCP tools match the existing REST surface; DALLAY-416 consumer
  can build immediately.
- Agent retries are safe — the same `idempotencyKey` produces the same
  `PublicationResult`, and `list_publications` reveals terminal state.
- Audit trail covers every tool call (read and write) with workspace
  isolation; the audit fact carries `publicationId` for write calls so a
  retried call is reconcilable.
- One new scope, one new realm change, one new table — minimal surface
  area.
- No new async surface for agents to learn.

### Negative

- The four existing read tools are finally wired up correctly; this means
  DALLAY-590 carries the prerequisite slice even though it is nominally a
  write-tools change.
- The `idempotency_records` table adds a write per tool call.
- Polling for terminal state introduces latency for agents that want
  immediate confirmation. Mitigated by `list_publications` being cheap.
- Six new error codes require consumer-side parsing; mitigated by the
  `category` field grouping them.

### Risks

- **Spring AI tool registration** — if the prerequisite slice fails to
  wire `@McpTool` correctly, the read path may regress in CI.
  *Mitigation*: BDD scenarios for every read tool before the write tools
  ship.
- **Idempotency table contention** — if a workspace has many concurrent
  MCP clients, the unique constraint may surface as a write conflict.
  *Mitigation*: dedup-check happens before the handler; collisions return
  the cached response without re-running the handler.
- **Audit emission point** — if the MCP adapter forgets to emit the audit
  fact, write activity becomes invisible. *Mitigation*: integration test
  that asserts the audit log contains an entry per write tool call.
- **Scope drift in consumer DALLAY-416** — if DALLAY-416 requests
  additional scopes (e.g. `mcp:media:write`), this ADR becomes a moving
  target. *Mitigation*: keep DALLAY-590 scope conservative; surface new
  scope requests as separate ADRs.
- **Spring AI JSON-RPC `id` availability** — if Spring AI does not surface
  the JSON-RPC `id` to the tool method, `clientToolCallId` becomes
  useless. *Mitigation*: verify in design phase; fall back to
  UUID-generated `clientToolCallId` or drop the field.

### Accepted trade-offs

- We accept that an agent retrying a `create_publication` without
  `idempotencyKey` MAY create duplicate publications. The contract
  documents this; agents that care MUST supply a key.
- We accept that `cancel_publication` / `delete_publication` are
  idempotent at the lifecycle level — repeated calls on an already
  terminal publication throw `PublicationStateTransitionException`, mapped
  to `publication_state_conflict`; the agent must treat this as success.
- We accept that no agent-side notification exists; agents poll. If a
  future change requires push, revisit Q2.
- We accept that conflict detection between concurrent agent creates is
  out of scope (the SPA has the same behaviour); fix is a separate change.

## Compliance and enforcement

- `McpToolInvocationAuthorizer` unit tests assert that a token without
  `mcp:publications:write` gets `insufficient_scope` for the five write
  tools.
- `McpToolMetadataTest` asserts the five new tool entries.
- `ResourceMetadataControllerTest` asserts `scopesSupported` includes
  `mcp:publications:write`.
- BDD scenarios in
  `server/smp/src/test/resources/features/mcp-write-tools.feature` (one
  happy path per tool, one failure mode per tool: missing scope,
  idempotency conflict, not found, state conflict).
- Audit emission test: assert that after a write tool call, the
  `mcp.audit` structured log contains an entry with the same
  `correlationId` and a non-null `publicationId`.
- Hexagonal architecture and Konsist tests (`HexagonalArchTest.kt`,
  `AggregateBoundaryTest.kt`, `IdentityOnlyAggregateCommunicationTest.kt`,
  `ValueObjectImmutabilityTest.kt`) MUST pass unchanged.
- Modulith tests (`ModularStructureTest.kt`,
  `ModularityVerificationTest.kt`) MUST pass unchanged.
- `backend-check` MUST pass: Detekt, unit tests, modularity.

## Verification

- `just backend-check` — Detekt + unit tests + modularity green.
- `just backend-test` — green.
- `just backend-bdd-fast` — green, including the new BDD scenarios.
- `just backend-test-postgres` — green (idempotency table migration
  tested).
- Manual: `npx @modelcontextprotocol/inspector --config
  mcp-inspector.config.json` — verify `tools/list` advertises 9 tools (4
  read + 5 write); verify write tools advertise `destructiveHint = true`.
- Manual: Keycloak realm — verify `mcp:publications:write` is granted
  only when explicitly requested; verify a token without the scope gets
  `insufficient_scope` on a write call.

## Migration or remediation

1. Run the `idempotency_records` migration as part of the change; do not
   introduce a separate change for it.
2. Realm-side: declare `mcp:publications:write` on the MCP client scope
   in the realm JSON; commit the JSON diff under `tools/compliance/` or
   the agreed keycloak-config location. Do NOT update production realms
   as part of the code merge — staging first.
3. Backfill: there are no existing agents with write tools, so no
   backfill needed for the new scope.
4. Audit fact format change is additive (new fields); existing consumers
   of structured logs MUST tolerate `publicationId: null` for read calls.

## Follow-up actions

- [ ] **spec phase**: tighten ADR-0019 wording; convert each "MUST" into a
      test in `tasks.md`.
- [ ] **spec phase**: confirm the `idempotencyKey` parameter is optional
      (omit-by-default) and document the `idempotency_conflict` error
      path explicitly.
- [ ] **spec phase**: confirm Spring AI 2.0 surfaces the JSON-RPC request
      `id` to the tool method; if not, drop `clientToolCallId` from the
      audit fact or generate one inside the adapter from
      `UUID.randomUUID()`.
- [ ] **spec phase**: confirm with DALLAY-416 that
      `list_publications(status=FAILED|BLOCKED|CANCELLED)` is sufficient
      for their polling; otherwise add a `get_publication` tool.
- [ ] **design phase**: choose the audit sink (structured log vs
      observability topic) before coding.
- [ ] **apply phase**: prerequisite slice — annotate 4 read tools,
      register `ToolCallbackProvider`, emit audit fact — must be green
      before write tools are added.

## Revisit conditions

- A consumer proves that `list_publications`-based polling is
  unacceptable (latency, cost, or correctness). Revisit Q2.
- LinkedIn publish latency changes such that a synchronous publish
  becomes acceptable. Revisit Q1.
- An agent requests a media upload path. New ADR for `mcp:media:write`.
- Keycloak 27+ ships CIMD and the `Idempotency-Key` header is no longer
  the simplest agent contract. Revisit Q3.
- Spring AI 2.x ships a write-side audit hook that supersedes the ad-hoc
  adapter emission. Revisit Decision 5.