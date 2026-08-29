# Tasks: MCP Write Tools for Publication Lifecycle

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 450–650 total; current PR likely 400+ |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | Current PR/branch: foundation + write prerequisites → follow-up branch 2: create/edit/delete → follow-up branch 3: cancel/retry + BDD/docs |
| Delivery strategy | feature-branch-chain |
| Chain strategy | feature-branch-chain |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: feature-branch-chain
400-line budget risk: High

Preserve the existing uncommitted PR 0 foundation on `feature/mcp-write-tools-01-foundation`; do not reset, discard, rename, or split it. The current PR/branch is unit 1, absorbing registration, security hardening, and write prerequisites. Unit 2 follows on the create/edit/delete branch, and unit 3 follows on the cancel/retry branch with BDD and docs.

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Foundation and write prerequisites | Current PR/branch | `feature/mcp-write-tools-01-foundation`; existing uncommitted PR 0; issue/Linear=DALLAY-590; preserve foundation files and add scope/idempotency groundwork. |
| 2 | Create, edit, and delete tools | Follow-up branch | `feature/mcp-write-tools-02-create-edit-delete`; follows unit 1; issue/Linear=DALLAY-590. |
| 3 | Cancel, retry, BDD, and docs | Follow-up branch | `feature/mcp-write-tools-03-cancel-retry`; follows unit 2; issue/Linear=DALLAY-590; include final gates. |

## Phase 1: Current PR Foundation Carry-Forward

- [ ] 1.1 Preserve and complete `@McpTool`/`@McpToolParam` on `PublicationTools.kt`, `ChannelTools.kt`, `ProviderTools.kt`, and `McpPingTool.kt`; write failing transport/schema tests first.
- [ ] 1.2 Verify the exact five-tool catalog and generated output schemas with `McpToolMetadataTest` and the `/api/mcp` `tools/list` smoke request.
- [ ] 1.3 Replace membership and permissive authorization stubs; test workspace mismatch, missing scope, read/write isolation, ping access, and handler non-dispatch.
- [ ] 1.4 Wire `McpToolInvocationAuditFact` emission for success, denial, and mapped errors; extend read BDD scenarios to assert payloads and audit non-interference.

## Phase 2: Current PR Write Foundation

- [ ] 2.1 Promote ADR-0019, register `mcp:publications:write`, update `ResourceMetadataController`, and configure the Keycloak realm/protocol mapper.
- [ ] 2.2 Add Liquibase `0001-mcp-idempotency-records.xml`, the MCP domain port, and reactive infrastructure entity/repository for workspace/principal/tool/key-hash lookup and cached JSON.
- [ ] 2.3 Add SHA-256 hashing and adapter-level `IdempotencyGuard`; test replay, malformed collision, plaintext non-persistence, and workspace/principal/tool isolation.
- [ ] 2.4 Add the `mcp-publications-write` 15/min bucket in `application.yml`, write metadata/hints, and mapper codes; cover rate-limit and error envelopes with BDD.

## Phase 3: Publication Write Tools

- [ ] 3.1 Add `create_publication`, `edit_publication`, and `delete_publication` to `PublicationTools.kt`, delegating existing mediator commands and applying idempotency, correlation IDs, validation, and ADR-0019 hints.
- [ ] 3.2 Test acknowledgements without blocking publish, required/optional parameters, not-found/state conflicts, write-scope denial, idempotency replay, and audit `publicationId` in unit and Cucumber suites.

## Phase 4: Completion and Verification

- [ ] 4.1 Add `cancel_publication` and `retry_publication` with mediator commands, idempotency, cancellation/state-conflict rules, destructive/idempotent hints, and correlation IDs.
- [ ] 4.2 Add cancel/retry BDD scenarios, FAILED/BLOCKED/CANCELLED recovery through `list_publications(status=...)`, and the final nine-tool catalog smoke.
- [ ] 4.3 Update `docs/mcp-server.md`, ADR index/source-of-truth references, and DALLAY-416 onboarding; run `just backend-check`, `just backend-bdd-fast`, and `just backend-test-postgres`.
