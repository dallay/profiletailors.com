# mcp-tool-registration Specification

## Purpose

Define how MCP tools become reachable through the Spring AI MCP transport so that every
declaration is also a discoverable, schema-typed tool on `/api/mcp`. This specification
closes the gap left by DALLAY-434, whose four read tools shipped as plain Kotlin classes
that never appeared in `tools/list`.

## Requirements

### Requirement: Spring AI annotation discovery

Methods exposed by the MCP server MUST carry `@McpTool(name, description)` from
`org.springframework.ai.mcp.annotation`. Each parameter MUST carry `@McpToolParam` so the
JSON schema reflects argument names, types, required-ness, and descriptions. The class
hosting the tool method MUST be a Spring bean (`@Component`) so the WebFlux starter's
auto-configuration discovers it. `generateOutputSchema = true` MUST be set on every read
tool so the schema appears in the `tools/list` response without an additional round-trip.

#### Scenario: Read tools are discovered as Spring beans

- GIVEN the four existing tools are annotated with `@McpTool` and their classes are
  `@Component`
- WHEN the Spring Boot context starts with `spring.ai.mcp.server.enabled=true`
- THEN `McpConfiguration` MUST register a `ToolCallbackProvider` that includes the four
  read methods.

#### Scenario: A tool method without `@McpTool` is not exposed

- GIVEN a class that hosts a `suspend fun` method but lacks `@McpTool`
- WHEN the MCP server returns `tools/list`
- THEN that method MUST NOT appear in the tool catalog.

### Requirement: Exact tool catalog after MCP server start

`tools/list` against `/api/mcp` MUST return exactly five tools and no others:
`mcp_ping` (transport health check, no scope required), `list_publications`, `get_calendar`,
`list_channels`, `list_providers`. After the write tools land (PR 2 and PR 3 of this
change), the catalog MUST grow to nine tools; further additions MUST require a new
requirement under this capability.

#### Scenario: Tools list returns the five declared tools and nothing else

- GIVEN the MCP server has started and a valid client performs `tools/list`
- WHEN the server responds
- THEN the response MUST contain exactly `mcp_ping`, `list_publications`, `get_calendar`,
  `list_channels`, `list_providers`
- AND the total tool count MUST equal five.

#### Scenario: Unknown tool name returns JSON-RPC -32601

- GIVEN a valid authenticated client invokes `tools/call` with `name="not_a_tool"`
- WHEN the server processes the request
- THEN the response MUST be a JSON-RPC error with `code = -32601` and `message` explaining
  the unknown tool.

### Requirement: Required argument validation

A `tools/call` request that omits a required `@McpToolParam` (declared non-null, no
default) MUST fail with JSON-RPC `code = -32602` and a message naming the missing
parameter. Nullable parameters and parameters with defaults MUST be optional in the
schema.

#### Scenario: Missing required parameter is rejected

- GIVEN `list_publications` requires the `from` and `to` parameters
- WHEN `tools/call` arrives with only `to`
- THEN the response MUST be a JSON-RPC error with `code = -32602`
- AND the message MUST name `from` as missing.
