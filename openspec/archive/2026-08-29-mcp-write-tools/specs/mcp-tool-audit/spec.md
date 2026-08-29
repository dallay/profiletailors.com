# mcp-tool-audit Specification

## Purpose

Make every MCP tool invocation auditable by emitting `McpToolInvocationAuditFact` on
each call, regardless of outcome. This specification closes the gap left by DALLAY-434,
where the fact was defined but never published.

## Requirements

### Requirement: Audit fact emitted on every invocation

`McpToolInvocationAuditFact` MUST be emitted on SUCCESS, DENIED, and ERROR outcomes. The
publisher MUST be wired as a Spring component so it runs on every tool call through the
MCP transport. Audit emission MUST NOT swallow tool outcomes; if the audit sink fails,
the failure MUST be logged at WARN and the original tool outcome MUST still be returned
to the agent.

#### Scenario: Successful read tool emits a SUCCESS fact

- GIVEN an authenticated workspace member invokes `list_publications` with a valid scope
- WHEN the handler returns a non-error `ToolResponse`
- THEN the audit sink MUST receive a `McpToolInvocationAuditFact` with
  `outcome = SUCCESS`.

#### Scenario: Insufficient scope emits a DENIED fact

- GIVEN a token without the required scope invokes a write tool
- WHEN the authorization check rejects the call
- THEN the audit sink MUST receive a `McpToolInvocationAuditFact` with
  `outcome = DENIED` and the missing scope in the payload.

#### Scenario: Handler error emits an ERROR fact

- GIVEN a tool handler throws an exception that is mapped by `McpErrorMapper`
- WHEN the exception is logged and the error is returned to the agent
- THEN the audit sink MUST receive a `McpToolInvocationAuditFact` with
  `outcome = ERROR` and the exception class name in the payload.

### Requirement: Audit fact fields

`McpToolInvocationAuditFact` MUST carry the following fields: `toolName`,
`workspaceId`, `principal`, `outcome`, `timestamp`. For write tools, the fact MUST also
carry `publicationId` when one is known from the response. `clientToolCallId` MUST be
populated only when the underlying transport exposes the JSON-RPC request `id` to the
tool; until the open question on Spring AI 2.0 visibility is resolved the field MUST be
null and the spec MUST NOT mandate it.

#### Scenario: Write tool fact includes the publication id

- GIVEN `create_publication` returns a `PublicationResult` with a non-null
  `publicationId`
- WHEN the audit emission runs
- THEN the emitted fact MUST include `publicationId = <returned id>`
- AND the `correlationId` MUST be the same UUID exposed on the agent-facing response.

#### Scenario: clientToolCallId is null until the transport precondition holds

- GIVEN no Spring AI 2.0 API exposes the JSON-RPC request `id` to `@McpTool` methods
- WHEN the audit emission runs
- THEN the emitted fact MUST have `clientToolCallId = null`
- AND the existing `correlationId` MUST still be present so agent retries can be
  reconciled.

### Requirement: Audit sink is operational

The audit sink MUST use the existing `mcp.audit` structured logger at INFO level until a
permanent sink is decided. Audit emission MUST NOT introduce a synchronous database
write that blocks the tool response. The publisher MUST log a correlation marker
(`mcp.audit.correlation=<correlationId>`) that operators can grep.
