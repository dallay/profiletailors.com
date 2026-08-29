# mcp-server Specification (Delta)

## Purpose

Capture the change to the existing `mcp-server` capability that registers the four
existing read tools and the new `mcp_ping` health check with the Spring AI MCP
transport. This delta is necessary because DALLAY-434 shipped the MCP module without
discovering the tools at runtime; this specification records the contract that the
**post-DALLAY-590** MCP server must satisfy.

## Requirements

### Requirement: Read tools are reachable through the MCP protocol

`tools/list` against `/api/mcp` MUST return the four read tools (`list_publications`,
`get_calendar`, `list_channels`, `list_providers`) plus `mcp_ping`. Each tool MUST be
backed by an `@McpTool`-annotated method on an `@Component` Spring bean and MUST be
visible to a fresh `tools/list` request without an extra initialization round-trip.

#### Scenario: A fresh client can read all four tool names

- GIVEN a freshly authenticated MCP client has not interacted with the server before
- WHEN the client performs `tools/list`
- THEN the response MUST include `mcp_ping`, `list_publications`, `get_calendar`,
  `list_channels`, and `list_providers`
- AND `tools/call` for each MUST execute without manual bean wiring.

#### Scenario: Read tool payload matches the existing mediator response

- GIVEN the read tools delegate to existing query handlers through `Mediator.send(query)`
- WHEN a tool is invoked with valid arguments and a valid scope
- THEN the response payload MUST match what direct invocation of the same query handler
  returns
- AND any difference MUST come only from the MCP-specific correlation and audit fields.

### Requirement: Authorization, audit, and workspace guards run on every call

The MCP server MUST enforce per-tool scope authorization (see `mcp-tool-authorization`),
MUST enforce workspace membership through a real tenancy query (replacing the
`Mono.just(true)` stub), and MUST emit an audit fact per call (see `mcp-tool-audit`).
A read call MUST NOT succeed without the matching read scope, MUST NOT return data
outside the caller's workspace, and MUST leave a SUCCESS audit fact on the
`mcp.audit` logger.

#### Scenario: Read call without workspace membership is rejected

- GIVEN a token whose `workspace_id` claim does not match any membership
- WHEN the membership query runs
- THEN the MCP server MUST return `ApplicationError` with `category = "authorization"`
  and `retryable = false`
- AND the underlying handler MUST NOT be invoked.

(Previously: DALLAY-434 shipped `McpWorkspaceMembershipChecker` returning `Mono.just(true)`,
which bypassed tenancy checks.)
