# MCP Server Consolidated Delta Specification

## Requirements

### Requirement: Stateless MCP Resource Server
The system MUST expose stateless Streamable HTTP at `POST /api/mcp`, configured by `spring.ai.mcp.server.streamable-http.mcp-endpoint` and `spring.ai.mcp.server.enabled`. SMP MUST publish only RFC 9728 Protected Resource Metadata for this resource; Keycloak owns authorization, token, revocation, registration, and authorization-server metadata endpoints.

#### Scenario: OAuth resource discovery
- GIVEN an unauthenticated request to `/api/mcp`
- WHEN authentication fails
- THEN `401` MUST include a `resource_metadata` URL
- AND the metadata MUST identify Keycloak as authorization server

### Requirement: Stable Read-Only Tool Catalog
The server MUST expose `list_publications`, `list_channels`, `get_calendar`, and `list_providers`. `tools/list` MUST return all four regardless of granted scopes. A valid query with no results MUST return an empty array, not an error.

#### Scenario: Catalog is independent of scopes
- GIVEN a valid token with either or neither MVP scope
- WHEN the client calls `tools/list`
- THEN all four tools MUST be returned
- AND no scope check MUST filter the catalog

#### Scenario: Empty query succeeds
- GIVEN an authorized workspace has no matching records
- WHEN any read tool is called with valid input
- THEN the relevant result collection MUST be `[]`
- AND the result MUST NOT be an error

### Requirement: Audience, Workspace, and Scope Separation
The RFC 8707 `resource` parameter and JWT `aud` MUST identify `https://api.profiletailors.com/api/mcp`. A separate `workspace_id` claim MUST identify the tenant. The resource server MUST validate audience and current principal membership in that workspace. Scope enforcement MUST occur at the `tools/call` invocation boundary after Spring AI resolves the tool, using an interceptor/decorator or adapter-shared `requireScope`; the security filter chain MUST only validate token, issuer, audience, expiry, and `workspace_id` presence.

#### Scenario: Audience and membership both pass
- GIVEN a valid token has the MCP audience and `workspace_id=A`
- WHEN a member of A calls an authorized tool
- THEN only workspace A data MAY be returned

#### Scenario: Missing tool scope is explicit
- GIVEN a valid workspace token lacks the called tool's scope
- WHEN the client calls `tools/call`
- THEN the server MUST return `403`
- AND the body MUST contain `required_scope`

### Requirement: MVP Scope Mapping
`mcp:channels:read` MUST authorize `list_channels`. `mcp:publications:read` MUST authorize `list_publications`, `get_calendar`, and `list_providers`. No write scope or write tool MAY be part of MVP.

#### Scenario: Read scope authorizes mapped tools
- GIVEN a member token has `mcp:publications:read`
- WHEN it calls any publication-mapped tool
- THEN the call MUST pass scope authorization

### Requirement: Tool Error Taxonomy
Invalid tool input or domain execution MUST return `ApplicationError`. `list_publications` and `get_calendar` MUST support `INVALID_DATE_RANGE`, `INVALID_TIMEZONE`, and `DATE_RANGE_TOO_LARGE`; `list_channels` MUST support `INVALID_CHANNEL_STATUS`; all tools MUST support `WORKSPACE_ACCESS_DENIED`, `RATE_LIMIT_EXCEEDED`, and `INTERNAL_ERROR`.

#### Scenario: Date and timezone validation errors
- GIVEN `list_publications` or `get_calendar` receives an invalid range, timezone, or oversized range
- WHEN input is validated
- THEN the error code MUST respectively be `INVALID_DATE_RANGE`, `INVALID_TIMEZONE`, or `DATE_RANGE_TOO_LARGE`

#### Scenario: Invalid channel status
- GIVEN `list_channels` receives an unsupported status
- WHEN input is validated
- THEN `INVALID_CHANNEL_STATUS` MUST be returned

#### Scenario: Cross-cutting tool failures
- GIVEN any tool encounters denied workspace access, rate limiting, or an unexpected failure
- WHEN the call is handled
- THEN the code MUST respectively be `WORKSPACE_ACCESS_DENIED`, `RATE_LIMIT_EXCEEDED`, or `INTERNAL_ERROR`
