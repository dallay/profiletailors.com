# mcp-tool-authorization Specification

## Purpose

Replace the placeholder `McpToolInvocationAuthorizer` (which accepted any `mcp:*` scope)
with strict per-tool scope enforcement so a read-only token can never invoke a write tool
and `mcp_ping` does not require a scope. This specification closes the authorization gap
that DALLAY-434 left behind.

## Requirements

### Requirement: Per-tool scope mapping

Every MCP tool MUST declare the scope it requires through `McpToolMetadata.registry`.
`McpToolInvocationAuthorizer` MUST look up the tool by name and reject the call when the
authenticated token does not contain the declared scope. The mapping MUST be:

- `list_publications` and `get_calendar` -> `mcp:publications:read`
- `list_channels` -> `mcp:channels:read`
- `list_providers` -> `mcp:providers:read`
- `create_publication`, `edit_publication`, `delete_publication`, `cancel_publication`,
  `retry_publication` -> `mcp:publications:write`
- `mcp_ping` -> no scope required

#### Scenario: Tool missing scope returns insufficient_scope error

- GIVEN a token without `mcp:channels:read`
- WHEN `tools/call` invokes `list_channels`
- THEN the MCP server MUST return an `ApplicationError` with
  `code = "insufficient_scope"`, `category = "authorization"`, `retryable = false`
- AND the handler MUST NOT be executed.

#### Scenario: Read token cannot invoke a write tool

- GIVEN a token carrying only `mcp:publications:read` and `mcp:channels:read`
- WHEN `tools/call` invokes `create_publication`
- THEN the server MUST return `insufficient_scope`
- AND no `Mediator.send(CreatePublicationCommand)` MUST run.

#### Scenario: Ping succeeds without any scope

- GIVEN a token with no MCP scopes
- WHEN `tools/call` invokes `mcp_ping`
- THEN the server MUST return a successful response
- AND the authorization check MUST be bypassed for the `mcp_ping` tool only.

### Requirement: Authorization runs after resolution but before handler dispatch

`McpToolInvocationAuthorizer` MUST run on every tool invocation regardless of tool name
and MUST execute after Spring AI has resolved the tool method but before the underlying
`Mediator.send(...)` call. Authorization MUST NOT be skipped when the class hosting the
tool is not yet `@Component`; the dependency from the authorization step on bean
discovery is therefore MUST, not MAY.

#### Scenario: Authorization fires even when the tool method is not annotated

- GIVEN the legacy state where a tool method lacks `@McpTool`
- WHEN the method is still invoked directly via a stray test or admin path
- THEN the `McpToolInvocationAuthorizer` MUST still resolve the requested scope from
  the metadata registry
- AND the call MUST be rejected if the scope is missing.

### Requirement: `mcp:publications:write` is a new scope

`mcp:publications:write` MUST be declared in the Keycloak realm on the MCP client scope
that issues MCP tokens and MUST be listed in `ResourceMetadataController.scopesSupported`
so RFC 9728 discovery advertises it. A token with `mcp:publications:write` SHOULD also
carry `mcp:publications:read` to inspect its own work; this is a Keycloak client-scope
grant choice, not a code change.

#### Scenario: Resource metadata advertises the write scope

- GIVEN the MCP server is started with the new scope declared
- WHEN a client performs `GET /.well-known/oauth-protected-resource`
- THEN the response MUST include `mcp:publications:write` in `scopes_supported`
- AND the response MUST still include the two existing read scopes.
