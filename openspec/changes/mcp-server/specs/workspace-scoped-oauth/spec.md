# Workspace-Scoped OAuth Specification

## Purpose
Define Authorization Code + PKCE with independently validated MCP audience and tenant context.

## Requirements

### Requirement: Pre-Flow Workspace Injection
Profile Tailors MUST resolve an authorized workspace before OAuth starts and send signed context or a pre-flow token to Keycloak. A Keycloak protocol mapper MUST read that context and emit `workspace_id`. Proposal Option A SHOULD be used for MVP; a client-supplied workspace MUST NOT be authoritative.

#### Scenario: Workspace is bound before browser login
- GIVEN a member selects workspace A in Profile Tailors
- WHEN Profile Tailors starts OAuth with signed workspace context
- THEN Keycloak MUST emit `workspace_id=A` after successful login and consent

#### Scenario: Unauthorized workspace cannot be injected
- GIVEN the principal is not a member of workspace B
- WHEN a flow attempts to bind workspace B
- THEN no token for workspace B MUST be issued

### Requirement: Browser Authorization
Keycloak MUST perform Authorization Code + `S256` PKCE browser login and consent for the requested MVP scopes.

#### Scenario: User completes browser login
- GIVEN a registered client, valid redirect URI, PKCE challenge, and signed workspace context
- WHEN the user authenticates and consents
- THEN Keycloak MUST issue a code exchangeable with the verifier

### Requirement: Audience and Workspace Validation
The RFC 8707 `resource` parameter and JWT `aud` MUST identify `https://api.profiletailors.com/api/mcp`; they MUST NOT identify a workspace. `workspace_id` MUST be a separate signed tenant claim. SMP MUST validate token signature, issuer, audience, expiry, `workspace_id` presence, and current principal membership in that workspace.

#### Scenario: Audience and membership are both required
- GIVEN a validly signed token has the MCP audience and `workspace_id=A`
- WHEN a current member of A calls a tool
- THEN SMP MAY execute it only in workspace A

#### Scenario: Wrong audience is rejected
- GIVEN a token has `workspace_id=A` but its audience is not the MCP endpoint
- WHEN it is presented to `/api/mcp`
- THEN SMP MUST return `401` and MUST NOT execute a tool

#### Scenario: Workspace access is denied
- GIVEN a token has the MCP audience and `workspace_id=A`
- BUT the principal is no longer a member of A
- WHEN any tool is called
- THEN `WORKSPACE_ACCESS_DENIED` MUST be returned and no query MUST execute

### Requirement: Tool Invocation Scope Enforcement
`mcp:channels:read` MUST authorize `list_channels`; `mcp:publications:read` MUST authorize `list_publications`, `get_calendar`, and `list_providers`. Scope checks MUST occur after Spring AI resolves `tools/call`, through an interceptor/decorator or shared `requireScope`. SecurityWebFilterChain MUST only validate token, issuer, audience, expiry, and `workspace_id` presence.

#### Scenario: Catalog ignores scopes
- GIVEN a valid token lacks one or both MVP scopes
- WHEN `tools/list` is called
- THEN all four tools MUST be returned

#### Scenario: Call enforces resolved tool scope
- GIVEN a valid workspace token lacks `mcp:channels:read`
- WHEN `tools/call` resolves `list_channels`
- THEN SMP MUST return `403`
- AND the body MUST contain `required_scope: "mcp:channels:read"`

### Requirement: Authentication Failures
Missing, expired, forged, or wrong-audience tokens MUST return `401` with RFC 9728 discovery metadata.

#### Scenario: Missing token returns discovery
- GIVEN `/api/mcp` receives no bearer token
- WHEN authentication is evaluated
- THEN `401` MUST include the Protected Resource Metadata URL
