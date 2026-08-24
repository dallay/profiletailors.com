# MCP Server Specification

## Purpose

Provide a stateless, OAuth-protected, read-only MCP resource server.

## Requirements

### Requirement: Stateless Endpoint and Stable Catalog

The system MUST expose `POST /api/mcp` with stateless Streamable HTTP and support `initialize`,
`tools/list`, and `tools/call`. Configuration MUST use
`spring.ai.mcp.server.streamable-http.mcp-endpoint` and `spring.ai.mcp.server.enabled`. `tools/list`
MUST always advertise exactly `list_publications`, `list_channels`, `get_calendar`, and
`list_providers`, without scope filtering.

#### Scenario: Tools list is stable across scopes

- GIVEN a valid token with any combination of MVP scopes
- WHEN `tools/list` is called
- THEN all four read-only tools MUST be returned

#### Scenario: Unknown method is rejected

- GIVEN an authenticated MCP request
- WHEN it names an unsupported method
- THEN JSON-RPC error `-32601` MUST be returned
- AND no domain query MUST execute

### Requirement: Read Tool Contracts

The tools MUST return safe workspace-scoped data: `list_publications(from,to,status[],channelId)`,
`list_channels(status)`, `get_calendar(from,to,status[],channelId,timezone)`, and
`list_providers()`. Outputs MUST omit credentials, tokens, SQL, stack traces, and internal secrets.
Valid queries with no matches MUST return empty arrays.

#### Scenario: Each tool returns its safe collection

- GIVEN an authorized workspace and valid input
- WHEN any one of the four tools is called
- THEN its structured collection MUST contain only workspace data
- AND an empty result MUST be `[]`, not an error

### Requirement: Invocation-Boundary Scope Enforcement

Spring AI MUST resolve the tool before scope enforcement. An interceptor/decorator or adapter-shared
`requireScope` MUST enforce `mcp:channels:read` for `list_channels` and `mcp:publications:read` for
the other three tools. The SecurityWebFilterChain MUST NOT parse JSON-RPC bodies and MUST only
validate token, issuer, audience, expiry, and `workspace_id` presence.

#### Scenario: Missing scope blocks tools call

- GIVEN a valid workspace-bound token lacks the resolved tool's scope
- WHEN `tools/call` invokes that tool
- THEN HTTP `403` MUST be returned
- AND the error body MUST contain `required_scope`

### Requirement: Tool Error Taxonomy

Tool failures MUST use `ApplicationError(code, category, message, retryable, correlationId)`.
`list_publications` and `get_calendar` MUST report `INVALID_DATE_RANGE`, `INVALID_TIMEZONE`, and
`DATE_RANGE_TOO_LARGE`; `list_channels` MUST report `INVALID_CHANNEL_STATUS`; every tool MUST report
`WORKSPACE_ACCESS_DENIED`, `RATE_LIMIT_EXCEEDED`, and `INTERNAL_ERROR` where applicable.

#### Scenario: Invalid publication or calendar range

- GIVEN `list_publications` or `get_calendar` receives an inverted or malformed range
- WHEN input is validated
- THEN `INVALID_DATE_RANGE` MUST be returned

#### Scenario: Invalid publication or calendar timezone

- GIVEN either date-based tool receives an unsupported timezone
- WHEN input is validated
- THEN `INVALID_TIMEZONE` MUST be returned

#### Scenario: Publication or calendar range is too large

- GIVEN either date-based tool receives a range above the allowed maximum
- WHEN input is validated
- THEN `DATE_RANGE_TOO_LARGE` MUST be returned

#### Scenario: Invalid channel status

- GIVEN `list_channels` receives an unsupported status
- WHEN input is validated
- THEN `INVALID_CHANNEL_STATUS` MUST be returned

#### Scenario: Workspace access denial applies to every tool

- GIVEN any of the four tools is called without current workspace membership
- WHEN workspace authorization is evaluated
- THEN `WORKSPACE_ACCESS_DENIED` MUST be returned

#### Scenario: Rate limiting applies to every tool

- GIVEN any of the four tools exceeds its request limit
- WHEN the next call is handled
- THEN `RATE_LIMIT_EXCEEDED` MUST be returned

#### Scenario: Internal failures apply to every tool

- GIVEN any of the four tools encounters an unexpected failure
- WHEN the failure is mapped
- THEN `INTERNAL_ERROR` MUST be returned
- AND no internal detail MUST be exposed
