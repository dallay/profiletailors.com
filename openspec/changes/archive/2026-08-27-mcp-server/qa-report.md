# QA Report — mcp-server

> Authored 2026-08-27 as part of Linear DALLAY-434 close-out housekeeping.
> This is not a live `sdd-qa` agent run. It summarises the acceptance-quality
> evidence captured at PR 4 merge time so the change can be archived cleanly.

## Acceptance map

| Acceptance criterion (proposal §Success Criteria) | Evidence | Result |
|---|---|---|
| Four tools return workspace-isolated data through `/api/mcp` | `PublicationTools.kt`, `ChannelTools.kt`, `ProviderTools.kt`; BDD in PR 4 | Met |
| PKCE browser login | RFC 8707 + Keycloak protocol mapper spike outcome; security configuration in PR 2 | Met |
| Discovery (`/.well-known/oauth-protected-resource`) | `ResourceMetadataController.kt`, gated by `SMP_MCP_ENABLED` | Met |
| Audience check on token | `McpJwtConverter` + security configuration | Met |
| Workspace isolation | `McpWorkspaceContextResolver`, `McpWorkspaceMembershipChecker`, BDD scenarios in PR 4 | Met |
| Scope check per tool | `McpToolInvocationAuthorizer`, audit fact records denials | Met |
| Provider catalog surfacing | `ProviderTools` lists `ListProviderCatalogQuery` results | Met |
| Audit | `McpToolInvocationAuditFact`, emitted on every invocation | Met |
| Rate limit | `McpRateLimitFilter` scoped to `/api/mcp/**` | Met |
| Test coverage | `just backend-check`, `just backend-test`, `just backend-bdd-fast` green at every PR merge | Met |

## BDD coverage (PR 4)

`server/smp/src/test/resources/features/` gained MCP scenarios covering:

- OAuth discovery via RFC 9728 protected resource metadata
- Workspace-scoped `tools/list` payload
- Workspace-scoped `tools/call` payload for each of the four read tools
- Workspace isolation: a token for workspace A cannot read workspace B data
- Audience rejection: a token issued for an unrelated audience is refused
- Scope rejection: missing scope on a tool call returns the documented error
- Rate limit: repeated `tools/call` beyond the documented budget returns the
  documented error and emits an audit fact

Glue lives under `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/glue/`
and uses the repository's `BddDatabaseSupport` plus the accepted test token
prefixes (`valid-token`, `e2e-*`, `register-*`, `pending-*`, `verified-*`,
`owner-*`).

## Not in scope for this change

- Write-tool QA. DALLAY-590 will require new BDD scenarios for every failure
  mode enumerated by ADR-0019 (or successor). This is intentionally not
  attempted here to avoid claiming coverage that does not exist.
- Load test for the rate-limit filter. The filter's per-workspace budget is
  configurable but not exercised by BDD beyond a single threshold assertion.
- Staging re-test of Keycloak 26 CIMD behaviour. Deferred to the follow-up
  ticket that consumes the spike recommendation.

## Verdict

**PASS** for archival purposes. Acceptance-relevant QA gates were exercised
by BDD at PR 4 merge time. No open CRITICAL / P0 / P1 issues were filed
against the read path. All remaining concerns are tracked in DALLAY-590.
