# Proposal: MCP Write Tools for Publication Lifecycle

- **Change**: `mcp-write-tools`
- **Linear**: DALLAY-590
- **Companion**: ADR-0019 (`openspec/changes/mcp-write-tools/adr-0019-mcp-write-tools.md`)
- **Consumer**: DALLAY-416 (AI-Powered Post Generator)
- **Related**: `openspec/changes/mcp-server/` (DALLAY-434, archived; read path was never wired)

## Intent

DALLAY-434 cleared CI and BDD but its four read tools and security stack were never actually wired to the Spring AI transport. `tools/list` returns zero tools today; audit, scope, and workspace-membership guards are stubs. DALLAY-590 must (1) make the four read tools reachable, (2) harden read-path security so agents can be trusted with mutations, and (3) add five write tools (`create_publication`, `edit_publication`, `delete_publication`, `cancel_publication`, `retry_publication`) gated on ADR-0019: enqueue-ack semantics, failure surfacing via `list_publications`, client-supplied `idempotencyKey`, and one new `mcp:publications:write` scope with per-tool enforcement.

## User decision on record

> The user chose to absorb the registration-and-security-hardening prerequisite (originally PR 0 of the broader MCP program) into DALLAY-590 rather than splitting it out. DALLAY-590 therefore carries both the fix for the broken read path and the new write capability. Recorded here so the durable contract reflects what shipped.

## Scope

### In Scope

- **Phase 0 (PR 0)** — `@McpTool` on 4 reads; `ToolCallbackProvider`; replace `McpWorkspaceMembershipChecker` stub; replace permissive `McpToolInvocationAuthorizer` with per-tool enforcement; emit `McpToolInvocationAuditFact` on every call; BDD asserts each read tool's payload (not just `200`).
- **Phase 1 (PR 1)** — ADR-0019; `mcp:publications:write` scope + realm config; `idempotency_records` table + repository; `mcp-publications-write` rate-limit bucket; 5 write error codes. No tool methods yet.

### Out of Scope

Stateful streaming / MCP `notifications` (DALLAY-434 §Out of Scope). Connected Applications registry (DALLAY-434). Media upload from agents (`mcp:media:write`) — separate ADR. REST-side `Idempotency-Key` header — MCP-only. Dedicated `get_publication` tool — `list_publications(status=...)` is v1. Re-opening DALLAY-434's verification record.

### Contradictions with `openspec/changes/mcp-server/proposal.md`

| DALLAY-434 claim | Reality |
|---|---|
| *"Four tools return workspace-isolated data through `/api/mcp`"* | Plain Kotlin classes, not `@Component` or `@McpTool`. `grep -rEn "@McpTool\|ToolCallback"` returns zero hits. |
| *"Missing scope returns 403"* | `McpToolInvocationAuthorizer` accepts any `mcp:*` scope. |
| *"Audit fact records denials"* | `McpToolInvocationAuditFact` is a data class with no publisher. |
| Workspace membership check | `McpWorkspaceMembershipChecker` is `Mono.just(true)`. |

## Capabilities

### New

- `mcp-tool-registration`: `@McpTool` discovery, JSON schema, `ToolCallbackProvider` for read and write tools.
- `mcp-tool-authorization`: per-tool scope enforcement replacing `scopes.any { it.startsWith("mcp:") }`.
- `mcp-tool-audit`: `McpToolInvocationAuditFact` emitted on every call with `publicationId` and `correlationId`; structured log sink.
- `mcp-write-tools`: five write adapters, `idempotency_records` persistence, `mcp:publications:write` scope, write error codes.

### Modified

- `mcp-server`: catalog 4 → 9; scope list +1; BDD moves from "transport answers JSON-RPC" to "tool executes and returns the right payload".

## Approach

### Architecture

`McpConfiguration.kt` gains a `ToolCallbackProvider`; Spring AI auto-config scans `@Component` classes for `@McpTool` once annotated. Each tool method is a thin suspending function delegating to `Mediator.send(command)` — domain rules untouched. `McpToolInvocationAuthorizer` looks up `McpToolMetadata.requiredScope(toolName)` and returns `insufficient_scope` on mismatch. `McpWorkspaceMembershipChecker` becomes a real tenancy query. `McpWriteAuditEmitter` (new) wraps every invocation (read + write), extracts `publicationId` from `PublicationResult`, writes the fact to `mcp.audit` structured logger at INFO. Idempotency lives in `idempotency_records` keyed by `(workspace_id, principal_id, tool_name, key_hash)`; MCP adapter checks before dispatch and returns cached `response_json` on collision. Errors reuse the existing `ApplicationError` shape — no new consumer branch.

### Delivery model (5 PRs rebasing onto `main`)

| PR | Branch | Content | ADR? |
|---|---|---|---|
| PR 0 | `feature/mcp-write-tools-00-foundation` | Registration + security hardening; BDD proves the 4 reads execute end-to-end. | No |
| PR 1 | `feature/mcp-write-tools-01-write-foundation` | ADR-0019 + scope + idempotency + rate-limit bucket + error codes. No tool methods. | Yes |
| PR 2 | `feature/mcp-write-tools-02-create-edit-delete` | `create_publication`, `edit_publication`, `delete_publication`. | No |
| PR 3 | `feature/mcp-write-tools-03-cancel-retry` | `cancel_publication`, `retry_publication`. | No |
| PR 4 | `feature/mcp-write-tools-04-bdd-docs` | BDD for every failure mode the ADR enumerates; docs; MCP Inspector walkthrough. | No |

### Rationale

PR 0 is independently mergeable — fixes the broken read path DALLAY-434 left and unblocks read-only consumers today. PR 1 is the architectural gate; every later PR is mechanical once the ADR is in. Splitting keeps each diff under the 400-line review budget. PRs 2 and 3 separate destructive-but-cached (delete, cancel) from side-effecting (create, edit, retry) so DALLAY-590 can land mid-cycle without losing the read-path safety net. PR 4 lands last so its scenarios stay green against the final wiring.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `server/smp/.../mcp/tools/{Publication,Channel,Provider}Tools.kt` | Modified | `@McpTool` annotations; 5 new write methods in `PublicationTools.kt`. |
| `server/smp/.../mcp/infrastructure/McpConfiguration.kt` | Modified | `ToolCallbackProvider` bean. |
| `server/smp/.../mcp/infrastructure/security/{McpToolInvocationAuthorizer,McpWorkspaceMembershipChecker}.kt` | Modified | Per-tool scope + real tenancy query. |
| `server/smp/.../mcp/infrastructure/McpToolInvocationAuditFact.kt` | Modified | + `publicationId`, `clientToolCallId`. |
| `server/smp/.../mcp/infrastructure/{McpErrorMapper,McpRateLimitFilter}.kt` | Modified | + 5 error codes; + `mcp-publications-write` bucket. |
| `server/smp/.../mcp/infrastructure/oauth/ResourceMetadataController.kt` | Modified | Extend `scopesSupported`. |
| `server/smp/.../mcp/application/{McpWriteAuditEmitter,IdempotencyRecordRepository}.kt` + Liquibase | New | Audit emission; `idempotency_records` table. |
| `server/smp/src/test/resources/features/mcp-write-tools.feature` + glue | New | Write BDD scenarios. |
| `openspec/specs/mcp-server/spec.md`, `openspec/specs/iam/spec.md` | Modified | Delta for catalog + scope. |
| Keycloak realm JSON | Modified | Declare `mcp:publications:write`. |
| `docs/mcp-server.md` | Modified | Tool catalog + onboarding. |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Read-path wiring regresses | Medium | BDD in PR 0 asserts payload content per tool. |
| `idempotencyKey` table contention | Low | Lookup before dispatch; collision returns cached response. |
| Audit emission forgotten on a path | Medium | Single `McpWriteAuditEmitter`; integration test asserts presence. |
| Realm drift (local vs staging) | Medium | Realm JSON mirrored in `tools/compliance/`; staging before prod. |
| Spring AI 2.0 omits JSON-RPC `id` | Low | Fallback: drop `clientToolCallId` or generate UUID in adapter. |
| DALLAY-416 polling latency unacceptable | Low | ADR-0019 §Revisit triggers `get_publication` follow-up. |
| Conflict detection gap (concurrent creates) | Inherited | Documented in ADR-0019 §Accepted trade-offs; fix out of scope. |
| Write consumer coupling to error codes | Medium | Reuse `ApplicationError`; `category` groups them. |

## Rollback Plan

| PR | Reversible by |
|---|---|
| PR 0 | Set `SMP_MCP_ENABLED=false`; reads revert to unreachable. |
| PR 1 | Liquibase rollback drops `idempotency_records`; realm reverts to read-only scopes. |
| PR 2 | Revert; scope, table, emitter remain; agents get `insufficient_scope` until re-merge. |
| PR 3 | Revert; cancel/retry gone; existing writes still work via REST. |
| PR 4 | Revert; removes new BDD + docs only. |

## Dependencies

Spring AI 2.0 BOM (pinned). Keycloak realm update for `mcp:publications:write` (local, staging, prod). ADR-0019 must be **Proposed → Accepted** before PR 1 lands. Liquibase tooling (in use). DALLAY-416 design coordination on whether `list_publications(status=...)` is sufficient.

## Success Criteria

- [ ] `tools/list` against `/api/mcp` returns 9 tools (4 read + 5 write).
- [ ] Each read tool executes its payload end-to-end (BDD asserts body).
- [ ] Token without `mcp:publications:write` gets `insufficient_scope` on the 5 write tools.
- [ ] Token with the scope succeeds on the 5 write tools.
- [ ] Replaying a write call with the same `idempotencyKey` returns the same `PublicationResult` without a second write.
- [ ] `mcp.audit` log contains an entry per tool call; write entries carry `publicationId`.
- [ ] `McpWorkspaceMembershipChecker` no longer returns `Mono.just(true)`.
- [ ] `just backend-check`, `just backend-test`, `just backend-bdd-fast`, `just backend-test-postgres` green.
- [ ] MCP Inspector walkthrough updated; manual run shows `destructiveHint` and `idempotentHint` advertised correctly.