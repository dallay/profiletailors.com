# Proposal: MCP Server Integration

## Intent

Expose read models to agents via workspace-isolated MCP without making SMP an Authorization Server.

## Scope

### In Scope

- Spring AI 2.0 embedded MCP; STATELESS Streamable HTTP at `/api/mcp`
- OAuth 2.1 Authorization Code + PKCE browser login
- Tools: `list_publications`, `list_channels`, `get_calendar`, `list_providers`
- Scopes: `mcp:channels:read`, `mcp:publications:read`; shared `ApplicationError`; audit/rate limits

### Out of Scope

- Writes, stateful streaming, Connected Applications registry, intermediate Authorization Server

## Capabilities

### New Capabilities

- `mcp-server`: Protocol/tools
- `oauth-mcp-client-registration`: Onboarding
- `workspace-scoped-oauth`: Tenant authorization

### Modified Capabilities

- `iam`: Audience, scopes, `workspace_id`

## Approach

- Thin `@McpTool` adapters invoke handlers through Mediator.
- SMP Resource Server publishes **only RFC 9728 Protected Resource Metadata for `/api/mcp`**.
  Keycloak Authorization Server publishes RFC 8414/OIDC metadata plus
  authorize/token/revoke/register. The MCP Resource Server does NOT impersonate the Authorization
  Server.
- DCR is confirmed supported by Keycloak. CIMD is a SHOULD in MCP Nov 2025; support is unverified.
  Spike CIMD; fallback: DCR + pre-registered clients.
- RFC 8707 `resource` identifies the MCP audience, not the workspace. `workspace_id` is a separate
  server-validated tenant claim.
- Workspace options: A) Profile Tailors determines workspace pre-flow and signs context; Keycloak
  consumes it via protocol mapper. B) Custom Keycloak authenticator calls Profile Tailors. C)
  Intermediate Authorization Server. **Recommendation: A for MVP—resolve before OAuth starts and
  inject via protocol mapper.**
- `SecurityWebFilterChain` validates token, issuer, audience, expiry, and `workspace_id` presence.
  An interceptor/decorator enforces scope after Spring AI resolves the tool.
- `tools/list` announces all four tools. Missing scope on `tools/call` returns 403 with required
  scope.
- Use `spring.ai.mcp.server.streamable-http.mcp-endpoint`; feature flag
  `spring.ai.mcp.server.enabled`, fed by `SMP_MCP_ENABLED`.

### Delivery Strategy

- PR 1: `feature/mcp-server-01-foundation` → `main`: spike, dependencies, skeleton; NO domain logic
- PR 2: `feature/mcp-server-02-security` → PR 1: resource security, discovery, workspace context; NO
  tools
- PR 3: `feature/mcp-server-03-tools` → PR 2: four tools, error mapping, audit, rate limit
- PR 4: `feature/mcp-server-04-verification` → PR 3: BDD, integration tests, docs

## Affected Areas

| Area                                                                                                   | Impact   | Description          |
|--------------------------------------------------------------------------------------------------------|----------|----------------------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/mcp/`                                               | New      | MCP/security/tools   |
| `server/smp/build.gradle.kts`, `server/smp/src/main/resources/application.yml`, `server/smp/src/test/` | Modified | Setup/verification   |
| Keycloak configuration                                                                                 | Modified | DCR/workspace mapper |

## Risks

| Risk                    | Likelihood | Mitigation                               |
|-------------------------|------------|------------------------------------------|
| Spring-AI compatibility | Medium     | PR 1 spike                               |
| CIMD unavailable        | Medium     | DCR + pre-registration                   |
| Tenant/scope bypass     | High       | Signed claim; audience/invocation checks |

## Rollback Plan

Set `SMP_MCP_ENABLED=false`; remove MCP beans/config/dependencies. REST unaffected. Keycloak owns
tokens/sessions; SMP persistence is not reused. Future Connected Applications need separate design.

## Dependencies

- Spring AI 2.0, Keycloak, Mediator

## Success Criteria

- [ ] Four tools return workspace-isolated data through `/api/mcp`
- [ ] PKCE/discovery/audience/workspace/scope/catalog/audit/rate-limit/test checks pass
