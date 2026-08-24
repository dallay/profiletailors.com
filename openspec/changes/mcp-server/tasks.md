# Tasks: MCP Server Integration

## Review Workload Forecast

| Field                   | Value                                                                      |
|-------------------------|----------------------------------------------------------------------------|
| Estimated changed lines | ≤ 400 per PR (each PR scoped to <400 lines)                                |
| 400-line budget risk    | Low (per-PR); cumulatively High for the whole change                       |
| Chained PRs recommended | Yes (locked by user mandate)                                               |
| Suggested split         | PR 1 → PR 2 → PR 3 → PR 4, each rebase-onto-main after the previous merges |
| Delivery strategy       | stacked-prs (mandated)                                                     |
| Chain strategy          | stacked-to-main                                                            |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: Low

### Work Units (Stacked PRs — strict base dependency)

| PR   | Branch                               | Base                    | Focus                                                   | Tasks   |
|------|--------------------------------------|-------------------------|---------------------------------------------------------|---------|
| PR 1 | `feature/mcp-server-01-foundation`   | `main`                  | Compatibility spike + deps + module skeleton            | T1–T10  |
| PR 2 | `feature/mcp-server-02-security`     | PR 1 → rebase to `main` | Resource security + OAuth discovery + workspace context | T11–T18 |
| PR 3 | `feature/mcp-server-03-tools`        | PR 2 → rebase to `main` | 4 read tools + error mapping + audit + rate limit       | T19–T25 |
| PR 4 | `feature/mcp-server-04-verification` | PR 3 → rebase to `main` | BDD + integration tests + documentation                 | T26–T33 |

> **Rebase rule**: PR 2/3/4 each open against the previous PR's branch. After PR N-1
> merges to `main`, the PR creator MUST re-target their branch onto `main` (no
> previous-PR changes in the diff). The author is responsible for the rebase.

---

## PR 1: Foundation — Compatibility Spike + Module Skeleton

**Branch**: `feature/mcp-server-01-foundation` → `main`
**Goal**: No tools, no security logic. Justified the API, wired the deps, scaffolded the module, and
proved the endpoint exists.

### Task 1: Spike — Spring AI 2.0 GA `@McpTool` annotation API

- **PR / Base**: PR 1 / main
- **Effort**: S
- **Depends on**: —
- **Files**: `scratch/spring-ai-spike/`, `openspec/changes/mcp-server/spikes/SPIKE_OUTCOME.md` (
  section 1)
- **Description**: Confirm `@McpTool`, `@McpToolParam`, `ToolCallbackProvider`, and STATELESS
  Streamable HTTP transport for WebFlux. Document import packages and protocol revision pinned by
  the starter.
- **TDD**: N/A (spike is exploration, not testable code). Convert learnings into a SPIKE_OUTCOME.md
  section.
- **Acceptance**: `@McpTool(name, description)` import path confirmed; STATELESS WebFlux transport
  confirmed; MCP protocol revision recorded; Spring Boot 4.0.7 + Kotlin 2.3.21 compatibility
  recorded.
- **Verification**: Spike module bootRun + `POST /api/mcp initialize` returns JSON-RPC response;
  SPIKE_OUTCOME.md section 1 written.

### Task 2: Spike — Keycloak DCR + CIMD compatibility

- **PR / Base**: PR 1 / main
- **Effort**: S
- **Depends on**: T1
- **Files**: `scratch/keycloak-cimd/`, `openspec/changes/mcp-server/spikes/SPIKE_OUTCOME.md` (
  section 2)
- **Description**: Stand up Keycloak 26+ realm. Verify (a) RFC 7591 DCR via
  `POST /oauth2/register`, (b) CIMD (draft-ietf-oauth-client-id-metadata-document) at `client_id`
  URL — separate outcomes. Record fallback path (pre-registered clients only).
- **TDD**: N/A. Record observations only.
- **Acceptance**: DCR status recorded (supported / not supported); CIMD status recorded (supported /
  not supported / partial); fallback path documented.
- **Verification**: `curl` matrix against local Keycloak; request/response recorded in
  SPIKE_OUTCOME.md section 2.

### Task 3: Spike — RFC 8707 `resource` parameter in Keycloak

- **PR / Base**: PR 1 / main
- **Effort**: S
- **Depends on**: T1
- **Files**: `scratch/rfc8707/`, `openspec/changes/mcp-server/spikes/SPIKE_OUTCOME.md` (section 3)
- **Description**: Verify RFC 8707 Resource Indicator handling in Keycloak — multi-audience tokens,
  `resource` query parameter adoption. Confirm `aud` claim contains the MCP URI.
- **TDD**: N/A.
- **Acceptance**: Multi-audience behavior documented; fallback (single audience
  `https://api.profiletailors.com/api/mcp` + `workspace_id` claim) recorded.
- **Verification**: Manual token inspection; SPIKE_OUTCOME.md section 3 written.

### Task 4: Spike — Workspace injection mechanism (Option A)

- **PR / Base**: PR 1 / main
- **Effort**: M
- **Depends on**: T1, T2
- **Files**: `scratch/workspace-context/`, `openspec/changes/mcp-server/spikes/SPIKE_OUTCOME.md` (
  section 4)
- **Description**: Decide JWS shape for `workspace_context`, whether the signed context travels as a
  login-URL param or auth-request param, and the Keycloak protocol mapper that copies the claim.
  This is Option A (recommended for MVP).
- **TDD**: N/A.
- **Acceptance**: JWS shape documented; transport mechanism (login-url param vs auth-request param)
  chosen; Keycloak protocol mapper configuration written; client config recommendation captured.
- **Verification**: SPIKE_OUTCOME.md section 4 written; PR 2 can start.

### Task 5: Spike — End-to-end with MCP Inspector + SPIKE_OUTCOME.md finalization

- **PR / Base**: PR 1 / main
- **Effort**: M
- **Depends on**: T1, T2, T3, T4
- **Files**: `scratch/mcp-inspector/`, `openspec/changes/mcp-server/spikes/SPIKE_OUTCOME.md` (final)
- **Description**: Run MCP Inspector against the spike module. Confirm `initialize` + `tools/list` +
  `tools/call` work in STATELESS mode. Confirm 401 carries RFC 9728 `resource_metadata` URL.
  Finalize SPIKE_OUTCOME.md with the recommended client config block.
- **TDD**: N/A.
- **Acceptance**: Inspector completes `initialize`; `tools/list` returns empty array;
  unauthenticated request → 401 + `WWW-Authenticate: Bearer resource_metadata="…"`; SPIKE_OUTCOME.md
  is COMPLETE and is the gate for PR 2.
- **Verification**: Inspector session recorded; SPIKE_OUTCOME.md final review.

### Task 6: Update Gradle version catalog + add Spring AI BOM

- **PR / Base**: PR 1 / main
- **Effort**: S
- **Depends on**: T5
- **Files**: `gradle/libs.versions.toml`, `server/smp/build.gradle.kts`
- **Description**: Add `springAi = "2.0.0"` to `[versions]`; add `spring-ai-bom`,
  `spring-ai-starter-mcp-server-webflux`, `spring-ai-mcp-server-webflux` to `[libraries]`. Import
  the BOM in `dependencyManagement`.
- **TDD**: RED — `./gradlew :server:smp:dependencies` fails resolving `spring-ai-bom`. GREEN — add
  catalog entries + import. REFACTOR — pin transitive versions the BOM does not manage.
- **Acceptance**: `./gradlew :server:smp:dependencies --refresh-dependencies` resolves cleanly;
  `mavenBom(libs.spring.ai.bom)` is imported alongside Spring Boot BOM.
- **Verification**: `just backend-build` exits 0;
  `./gradlew :server:smp:dependencies | grep spring-ai` shows the BOM and starter.

### Task 7: Add `spring-ai-starter-mcp-server-webflux` dependency

- **PR / Base**: PR 1 / main
- **Effort**: XS
- **Depends on**: T6
- **Files**: `server/smp/build.gradle.kts`
- **Description**: Add `implementation(libs.spring.ai.starter.mcp.server.webflux)` to `smp`
  dependencies.
- **TDD**: RED — `gradle build` fails (no starter). GREEN — add dependency. REFACTOR — none.
- **Acceptance**: Starter present in the `:server:smp` dependency graph.
- **Verification**: `./gradlew :server:smp:dependencies | grep spring-ai-starter-mcp-server-webflux`
  shows the resolved artifact.

### Task 8: Create `mcp` bounded-context skeleton

- **PR / Base**: PR 1 / main
- **Effort**: S
- **Depends on**: T7
- **Files**: `server/smp/src/main/kotlin/com/profiletailors/smp/mcp/ModuleMetadata.kt` + empty
  subpackages `adapter/`, `application/`, `infrastructure/`, `infrastructure/oauth/`
- **Description**: Scaffold the `mcp` bounded context per design. `ModuleMetadata` is the only
  public API; everything else is internal.
- **TDD**: RED — `:server:smp:archTest` complains about an unreferenced module. GREEN — add
  `ModuleMetadata` + empty subpackages. REFACTOR — public-API surface sized to the read tools (PR
  3).
- **Acceptance**: Spring Modulith recognises `mcp` as a standalone context; `archTest` passes; no
  `@McpTool` beans yet.
- **Verification**: `just backend-check` exits 0; `:server:smp:test --tests *Modulith*` passes.

### Task 9: Configure `application.yml` MCP + Profile Tailors keys

- **PR / Base**: PR 1 / main
- **Effort**: S
- **Depends on**: T6
- **Files**: `server/smp/src/main/resources/application.yml`, `.env.example`
- **Description**: Add
  `spring.ai.mcp.server.{enabled: ${SMP_MCP_ENABLED:false}, protocol: STATELESS, type: ASYNC, streamable-http.mcp-endpoint: /api/mcp}`
  and `app.mcp.{resource-uri, required-audience, oauth.issuer, oauth.scopes[]}` blocks. Document
  `SMP_MCP_ENABLED` in `.env.example`.
- **TDD**: RED — slice test asserts `SMP_MCP_ENABLED=false` ⇒ no `/api/mcp` exposure (test fails, no
  config). GREEN — add YAML. REFACTOR — group keys under `app.mcp.*` consistently.
- **Acceptance**: `SMP_MCP_ENABLED=false` ⇒ Spring AI transport bean excluded; endpoint is
  `/api/mcp`; scopes list includes `mcp:channels:read` and `mcp:publications:read`.
- **Verification**: `just backend-run` with `SMP_MCP_ENABLED=false`; `curl POST /api/mcp` returns
  401 (auth) and never 404.

### Task 10: NO-TOOL acceptance — server starts, `/api/mcp` returns 401

- **PR / Base**: PR 1 / main
- **Effort**: S
- **Depends on**: T8, T9
- **Files**: `server/smp/src/test/kotlin/com/profiletailors/smp/mcp/infrastructure/McpWiringTest.kt`
- **Description**: Slice test proving the server boots with `SMP_MCP_ENABLED=true`, no tools are
  registered, and an unauthenticated `POST /api/mcp` returns 401 with the discovery header (
  placeholder header is fine; contents checked in PR 2).
- **TDD**: RED — `McpWiringTest` fails (no `/api/mcp` exposure). GREEN — wire skeleton + config.
  REFACTOR — extract helper to assert 401 + `WWW-Authenticate` presence.
- **Acceptance**: Server boots; `tools/list` returns `[]`; 401 is returned with
  `WWW-Authenticate: Bearer ...` header.
- **Verification**: `just backend-test` passes the new slice test; manual
  `curl -i -X POST http://localhost:8080/api/mcp` returns 401.

**PR 1 Verification**: `just backend-build` passes; `just backend-test` passes; manual
`curl POST /api/mcp` returns 401.

---

## PR 2: Security + OAuth Discovery

**Branch**: `feature/mcp-server-02-security` → base PR 1
**Rebase note**: After PR 1 merges to `main`, the PR creator MUST re-target this branch onto
`main` (no PR 1 changes in the diff). The author is responsible for the rebase.
**Goal**: Resource security, OAuth discovery, workspace context. ONE internal ping tool behind a
profile flag — no production tools.

### Task 11: `ResourceMetadataController` for RFC 9728

- **PR / Base**: PR 2 / PR 1
- **Effort**: M
- **Depends on**: T10
- **Files**:
  `server/smp/src/main/kotlin/com/profiletailors/smp/mcp/infrastructure/oauth/ResourceMetadataController.kt`,
  `server/smp/src/test/kotlin/com/profiletailors/smp/mcp/infrastructure/oauth/ResourceMetadataControllerTest.kt`
- **Description**: Serve `GET /.well-known/oauth-protected-resource/api/mcp` (and root path)
  returning `{resource, authorization_servers, scopes_supported, bearer_methods_supported}`.
  Publicly readable (no auth). **DO NOT** serve `/.well-known/oauth-authorization-server` — Keycloak
  owns that.
- **TDD**: RED — `ResourceMetadataControllerTest` asserts JSON shape (test fails, no controller).
  GREEN — implement controller. REFACTOR — pull constants into `McpProperties`.
- **Acceptance**: Endpoint returns RFC 9728 JSON; `scopes_supported` =
  `["mcp:channels:read","mcp:publications:read"]`; `authorization_servers` points to Keycloak realm;
  no auth required.
- **Verification**: `curl /.well-known/oauth-protected-resource/api/mcp` returns expected JSON;
  slice test passes.

### Task 12: Extend `JwtAuthenticationConverter` for MCP audience + `workspace_id` claim

- **PR / Base**: PR 2 / PR 1
- **Effort**: M
- **Depends on**: T11
- **Files**:
  `server/smp/src/main/kotlin/com/profiletailors/smp/mcp/infrastructure/McpJwtConverter.kt`,
  `server/smp/src/test/kotlin/com/profiletailors/smp/mcp/infrastructure/McpJwtConverterTest.kt`
- **Description**: Compose the existing `JwtPrincipalAuthenticationConverter` with a customizer
  that (a) enforces `aud` contains `app.mcp.resource-uri` (RFC 8707), (b) extracts `workspace_id`
  claim into the principal, (c) rejects tokens missing `workspace_id`.
- **TDD**: RED — tests assert audience rejection + workspace_id extraction (fail, no converter).
  GREEN — implement converter. REFACTOR — add helper for `Jwt.getClaimAsString("workspace_id")`
  parsing.
- **Acceptance**: Token with wrong `aud` → `401`; token without `workspace_id` → `401`; valid
  token → principal carries `workspace_id`.
- **Verification**: `McpJwtConverterTest` slice tests pass for happy + 3 failure modes.

### Task 13: Extend `SecurityWebFilterChain` for `/api/mcp` path

- **PR / Base**: PR 2 / PR 1
- **Effort**: M
- **Depends on**: T12
- **Files**:
  `server/smp/src/main/kotlin/com/profiletailors/smp/mcp/infrastructure/McpSecurityConfiguration.kt`,
  `server/smp/src/test/kotlin/com/profiletailors/smp/mcp/infrastructure/McpSecurityConfigurationTest.kt`
- **Description**: Add a `SecurityWebFilterChain` scoped to `/api/mcp/**`. Validate token
  signature + `iss` + `aud` + `exp` + `workspace_id` presence. **DO NOT** parse JSON-RPC body. The
  401 response MUST carry `WWW-Authenticate: Bearer realm="mcp", resource_metadata="<rfc9728-url>"`.
- **TDD**: RED — slice test asserts 401 with `WWW-Authenticate` header (fails, no chain). GREEN —
  implement chain. REFACTOR — extract `buildResourceMetadataUrl(exchange)` helper.
- **Acceptance**: Unauthenticated → 401 + `WWW-Authenticate`; wrong audience → 401; expired token →
  401; REST endpoints unchanged.
- **Verification**: `McpSecurityConfigurationTest` passes; `curl -i POST /api/mcp` returns 401 +
  `WWW-Authenticate: Bearer resource_metadata="..."`.

### Task 14: `McpWorkspaceContextResolver`

- **PR / Base**: PR 2 / PR 1
- **Effort**: S
- **Depends on**: T13
- **Files**:
  `server/smp/src/main/kotlin/com/profiletailors/smp/mcp/application/McpWorkspaceContextResolver.kt`,
  `server/smp/src/test/kotlin/com/profiletailors/smp/mcp/application/McpWorkspaceContextResolverTest.kt`
- **Description**: Extract `workspace_id` from the validated JWT and push it into
  `RequestContextStore`. **Ignore** the `X-Workspace-Id` header for `/api/mcp` traffic.
- **TDD**: RED — test asserts `RequestContextStore.currentWorkspaceId()` returns the JWT value (
  fails, no resolver). GREEN — implement. REFACTOR — share the principal-extraction helper with T12.
- **Acceptance**: `RequestContextStore.currentWorkspaceId()` returns the JWT-derived value;
  `X-Workspace-Id` header is silently ignored on `/api/mcp`.
- **Verification**: Unit tests using MockK stub `ServerWebExchange` + `Jwt`.

### Task 15: `McpToolInvocationAuthorizer` (interceptor/decorator, NOT a WebFilter)

- **PR / Base**: PR 2 / PR 1
- **Effort**: M
- **Depends on**: T13
- **Files**:
  `server/smp/src/main/kotlin/com/profiletailors/smp/mcp/infrastructure/McpToolInvocationAuthorizer.kt`,
  `server/smp/src/main/kotlin/com/profiletailors/smp/mcp/adapter/McpToolMetadata.kt`,
  `server/smp/src/test/kotlin/com/profiletailors/smp/mcp/infrastructure/McpToolInvocationAuthorizerTest.kt`
- **Description**: Static tool→scope map consulted at tool invocation (after Spring AI resolves the
  bean). Reject when `Jwt.getClaimAsString("scope")` lacks the required scope. Return 403 +
  `WWW-Authenticate: Bearer error="insufficient_scope", scope="<required>"` + body
  `{ "required_scope": "<required>", "granted_scopes": [...] }`. **`McpToolAuthorizationFilter` as a
  WebFilter is FORBIDDEN** — the security chain must never parse JSON-RPC bodies.
- **TDD**: RED — authorizer tests fail (no class). GREEN — implement map + `requireScope`.
  REFACTOR — add second style (BeanPostProcessor wrapping `ToolCallback`) only if tool count grows
  above MVP.
- **Acceptance**: `list_channels` requires `mcp:channels:read`; publications/calendar/providers
  require `mcp:publications:read`; missing scope → 403 with `required_scope` in body.
- **Verification**: Unit tests for every tool against 3 grant profiles (no scope, wrong scope, right
  scope).

### Task 16: Workspace membership validator (consults existing tenancy module)

- **PR / Base**: PR 2 / PR 1
- **Effort**: S
- **Depends on**: T14
- **Files**:
  `server/smp/src/main/kotlin/com/profiletailors/smp/mcp/application/McpWorkspaceMembershipChecker.kt`,
  `server/smp/src/test/kotlin/com/profiletailors/smp/mcp/application/McpWorkspaceMembershipCheckerTest.kt`
- **Description**: Authoritative check that the JWT's `sub` holds a current membership grant for the
  token's `workspace_id`. Consults existing tenancy module; one read per request, short-lived cache
  allowed.
- **TDD**: RED — test asserts cross-workspace token is rejected (fails, no checker). GREEN —
  implement lookup. REFACTOR — bound the cache TTL via `McpProperties`.
- **Acceptance**: Token whose `sub` lost membership → 403 `workspace_mismatch`; active member →
  advisory pass.
- **Verification**: Slice test with mocked membership store; rejects non-member; passes member.

### Task 17: Internal `mcp_ping` tool for end-to-end security testing

- **PR / Base**: PR 2 / PR 1
- **Effort**: XS
- **Depends on**: T15, T16
- **Files**: `server/smp/src/main/kotlin/com/profiletailors/smp/mcp/adapter/PingTool.kt`,
  `server/smp/src/test/kotlin/com/profiletailors/smp/mcp/adapter/PingToolTest.kt`
- **Description**: ONE technical `@McpTool` (`mcp_ping`) registered ONLY behind
  `@Profile("mcp-internal")` or `app.mcp.internal-tools-enabled`. Returns workspace_id +
  correlation_id. Used for security end-to-end testing. NOT a production tool.
- **TDD**: RED — `PingToolTest` fails (no tool). GREEN — implement + register behind profile.
  REFACTOR — none.
- **Acceptance**: With `mcp-internal` profile active, `tools/list` includes `mcp_ping`; without it,
  `mcp_ping` is absent.
- **Verification**: Boot with/without `mcp-internal` profile; `tools/list` content matches.

### Task 18: NO-PRODUCTION-TOOL acceptance — security end-to-end

- **PR / Base**: PR 2 / PR 1
- **Effort**: M
- **Depends on**: T17
- **Files**:
  `server/smp/src/test/kotlin/com/profiletailors/smp/mcp/security/McpSecurityEndToEndTest.kt`
- **Description**: Full security chain test with `mcp-internal` profile: `mcp_ping` works with right
  token + scope; fails with 401 (no token) / 403 (wrong scope) / 403 (wrong workspace).
- **TDD**: RED — test fails (no profile-gated tool). GREEN — add profile + test. REFACTOR — extract
  `Tokens.mcpReader(workspace, scopes)` builder.
- **Acceptance**: 401 path; 403 insufficient_scope; 403 workspace_mismatch; 200 with right token.
- **Verification**: `McpSecurityEndToEndTest` passes all 4 paths.

**PR 2 Verification**: `McpSecurityEndToEndTest` passes; `curl POST /api/mcp` returns 401 without
token / 403 with wrong scope; `tools/list` (with `mcp-internal` profile) shows `mcp_ping` only.

---

## PR 3: 4 Read Tools + Error Mapping + Audit + Rate Limit

**Branch**: `feature/mcp-server-03-tools` → base PR 2
**Rebase note**: After PR 2 merges to `main`, the PR creator MUST re-target this branch onto
`main` (no PR 2 changes in the diff).
**Goal**: Four read-only tools, scope-to-tool mapping, audit, rate limit. No BDD, no docs.

### Task 19: `McpErrorMapper` with full taxonomy

- **PR / Base**: PR 3 / PR 2
- **Effort**: M
- **Depends on**: T18
- **Files**:
  `server/smp/src/main/kotlin/com/profiletailors/smp/mcp/infrastructure/McpErrorMapper.kt`,
  `server/smp/src/main/kotlin/com/profiletailors/smp/shared/error/ApplicationError.kt`,
  `server/smp/src/test/kotlin/com/profiletailors/smp/mcp/infrastructure/McpErrorMapperTest.kt`
- **Description**: Convert domain exceptions to
  `CallToolResult(isError=true, content=TextContent(JSON of ApplicationError))`. Codes:
  `invalid_date_range`, `invalid_timezone`, `date_range_too_large`, `invalid_channel_status`,
  `rule_violation`, `forbidden`, `workspace_mismatch`, `rate_limit_exceeded`, `internal`. Includes
  `correlationId` from `RequestContextStore`. Never exposes stack traces, SQL, or cross-workspace
  IDs. Tool-appropriate codes only — `publication_not_found` / `channel_disconnected` are
  intentionally absent (read-only tools).
- **TDD**: RED — `McpErrorMapperTest` covers each exception branch (fails, no mapper). GREEN —
  implement mapper + `ApplicationError`. REFACTOR — centralise the `correlationId` retrieval.
- **Acceptance**: One-to-one mapping per spec; `internal` mapping logs full stack but returns a
  redacted message; `correlationId` always present.
- **Verification**: `McpErrorMapperTest` passes every branch.

### Task 20: `McpPublicationTools` (`list_publications`, `get_calendar`)

- **PR / Base**: PR 3 / PR 2
- **Effort**: M
- **Depends on**: T19, T15
- **Files**:
  `server/smp/src/main/kotlin/com/profiletailors/smp/mcp/adapter/PublicationToolsAdapter.kt`,
  `server/smp/src/test/kotlin/com/profiletailors/smp/mcp/adapter/PublicationToolsAdapterTest.kt`
- **Description**: `@McpTool` beans delegating to `ListPublicationsQuery` and
  `GetCalendarPublicationsQuery` via `mediator.send(...)`. Inputs: `from`, `to` ISO-8601; optional
  `status[]`, `channelId`, `timezone`. Output is safe summary (no credentials, no internal IDs other
  than workspace-scoped ones).
- **TDD**: RED — slice test mocks `Mediator` and asserts `ListPublicationsQuery` dispatch (fails, no
  adapter). GREEN — implement adapters. REFACTOR — extract `parseRange(from, to)` validator.
- **Acceptance**: Tools appear in `tools/list` with JSON schemas; range outside workspace data
  returns `[]`, not an error; invalid range → `invalid_date_range` via `McpErrorMapper`.
- **Verification**: `PublicationToolsAdapterTest` passes; integration test asserts handler wiring.

### Task 21: `McpChannelTools` (`list_channels`)

- **PR / Base**: PR 3 / PR 2
- **Effort**: S
- **Depends on**: T19, T15
- **Files**: `server/smp/src/main/kotlin/com/profiletailors/smp/mcp/adapter/ChannelToolsAdapter.kt`,
  `server/smp/src/test/kotlin/com/profiletailors/smp/mcp/adapter/ChannelToolsAdapterTest.kt`
- **Description**: `@McpTool list_channels` delegating to `ListConnectedChannelsQuery`. Optional
  `status` filter. Output omits `providerAccessToken`, `secret`, and any OAuth refresh tokens.
- **TDD**: RED — slice test asserts `ListConnectedChannelsQuery` dispatch + secrets omission (fails,
  no adapter). GREEN — implement. REFACTOR — share a contract-level `assertNoSecrets(content)` test
  fixture.
- **Acceptance**: Tool visible in `tools/list` regardless of scope (catalog is stable); output never
  contains `providerAccessToken` / `secret`.
- **Verification**: `ChannelToolsAdapterTest` slice + integration test asserts response payload
  fields.

### Task 22: `McpProviderTools` (`list_providers`)

- **PR / Base**: PR 3 / PR 2
- **Effort**: S
- **Depends on**: T19, T15
- **Files**:
  `server/smp/src/main/kotlin/com/profiletailors/smp/mcp/adapter/ProviderToolsAdapter.kt`,
  `server/smp/src/test/kotlin/com/profiletailors/smp/mcp/adapter/ProviderToolsAdapterTest.kt`
- **Description**: `@McpTool list_providers` delegating to `ListProviderCatalogQuery`. No inputs.
  Output is provider availability metadata only.
- **TDD**: RED — slice test asserts `ListProviderCatalogQuery` dispatch (fails, no adapter). GREEN —
  implement. REFACTOR — none.
- **Acceptance**: Tool visible in `tools/list` regardless of scope; output contains provider
  metadata only, no secrets.
- **Verification**: `ProviderToolsAdapterTest` slice + integration test.

### Task 23: Scope-to-tool mapping table (MVP only)

- **PR / Base**: PR 3 / PR 2
- **Effort**: XS
- **Depends on**: T15, T20, T21, T22
- **Files**: `server/smp/src/main/kotlin/com/profiletailors/smp/mcp/adapter/McpToolMetadata.kt`,
  `server/smp/src/test/kotlin/com/profiletailors/smp/mcp/adapter/McpToolMetadataTest.kt`
- **Description**: Static map: `list_channels` → `mcp:channels:read`; `list_publications`,
  `get_calendar`, `list_providers` → `mcp:publications:read`. **DO NOT** add
  `mcp:publications:write` — Phase 5 deferred. Each `@McpTool(description = ...)` includes the
  required scope in natural language.
- **TDD**: RED — table test asserts exact mapping (fails, no metadata). GREEN — implement table.
  REFACTOR — generate from annotation processor only if the table grows above 6 entries.
- **Acceptance**: `tools/list` returns all 4 tools regardless of scopes; `tools/call` enforces
  scope; 403 carries `required_scope`.
- **Verification**: `McpToolMetadataTest` passes; `McpSecurityEndToEndTest` (PR 2) re-run shows 4
  tools advertised.

### Task 24: Audit log for every tool invocation

- **PR / Base**: PR 3 / PR 2
- **Effort**: S
- **Depends on**: T20, T21, T22
- **Files**:
  `server/smp/src/main/kotlin/com/profiletailors/smp/mcp/infrastructure/McpToolInvocationAuditFact.kt`,
  `server/smp/src/test/kotlin/com/profiletailors/smp/mcp/infrastructure/McpToolInvocationAuditFactTest.kt`
- **Description**: Emit an `AuditHook` fact on every tool invocation: tool name, scope checked,
  granted scopes, `workspace_id`, `correlation_id`, outcome (allow/deny). Reuses existing
  `AuditHook` infrastructure.
- **TDD**: RED — test asserts the fact is emitted with the right fields (fails, no fact). GREEN —
  implement. REFACTOR — share the `AuditHook.emit` helper.
- **Acceptance**: Every allow + every deny produces one audit fact; workspace_id + correlation_id
  are always populated.
- **Verification**: `McpToolInvocationAuditFactTest` passes; integration test asserts `AuditHook`
  captor sees the fact.

### Task 25: `McpRateLimitFilter` reusing `InMemoryRateLimitAdapter`

- **PR / Base**: PR 3 / PR 2
- **Effort**: M
- **Depends on**: T24
- **Files**:
  `server/smp/src/main/kotlin/com/profiletailors/smp/mcp/infrastructure/McpRateLimitFilter.kt`,
  `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/RateLimitConfiguration.kt`,
  `server/smp/src/test/kotlin/com/profiletailors/smp/mcp/infrastructure/McpRateLimitFilterTest.kt`
- **Description**: Reuse `InMemoryRateLimitAdapter`. Add bucket names `mcp-channels-read` (60/min
  per workspace) and `mcp-publications-read` (30/min per workspace). On bucket exhaustion:
  `CallToolResult(isError=true, application-error="rate_limit_exceeded")` AND `Retry-After` header.
- **TDD**: RED — slice test fires N+1 requests and asserts 429/`rate_limit_exceeded` (fails, no
  filter). GREEN — implement filter + bucket wiring. REFACTOR — pull bucket capacities into
  `McpProperties`.
- **Acceptance**: Exceeding bucket → `rate_limit_exceeded`; `Retry-After` header set; buckets are
  per workspace + per tool.
- **Verification**: `McpRateLimitFilterTest` passes; integration test asserts header.

**PR 3 Verification**: 4 tools registered; `tools/list` returns all 4 regardless of scopes;
`tools/call` enforces scope; 403 carries `required_scope`; rate-limit exhausted →
`rate_limit_exceeded`.

---

## PR 4: Verification + Documentation

**Branch**: `feature/mcp-server-04-verification` → base PR 3
**Rebase note**: After PR 3 merges to `main`, the PR creator MUST re-target this branch onto
`main` (no PR 3 changes in the diff).
**Goal**: BDD coverage, integration tests, docs. Close MVP.

### Task 26: BDD feature file `mcp-tools.feature`

- **PR / Base**: PR 4 / PR 3
- **Effort**: M
- **Depends on**: T25
- **Files**: `server/smp/src/test/resources/features/mcp-tools.feature`
- **Description**: Cucumber scenarios: happy path + auth failure + validation failure per tool,
  workspace isolation, OAuth discovery. Tags `@mcp @smoke @fast`.
- **TDD**: RED — BDD scenarios fail (no glue). GREEN — write scenarios. REFACTOR — group scenarios
  per tool with `Background:` for workspace + token.
- **Acceptance**: All 4 tools covered; workspace isolation scenario proves workspace B data is
  unreachable; OAuth discovery scenario asserts `resource_metadata` URL is well-formed.
- **Verification**: `just backend-bdd-fast` runs the feature; all scenarios pass.

### Task 27: BDD glue — `McpToolsBddSteps.kt`

- **PR / Base**: PR 4 / PR 3
- **Effort**: M
- **Depends on**: T26
- **Files**: `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/glue/McpToolsBddSteps.kt`
- **Description**: Step glue for `POST /api/mcp` with `WebTestClient`, `Authorization` header (mcp-*
  bearer), `Accept: application/vnd.api.v1+json`, captured `latestResponse`. Reuses
  `BddDatabaseSupport.resetDatabase()` and workspace seeding helpers.
- **TDD**: RED — glue missing, scenarios fail. GREEN — implement glue. REFACTOR — share
  `mcp(reader/publisher)Workspace(workspaceId, scopes)` token builder.
- **Acceptance**: Glue wires `BddDatabaseSupport` + `WebTestClient` correctly; bearer-token builder
  accepts `mcp-*` prefixes.
- **Verification**: `just backend-bdd-fast` passes.

### Task 28: OAuth integration test (discovery → login → workspace binding → token exchange)

- **PR / Base**: PR 4 / PR 3
- **Effort**: M
- **Depends on**: T27
- **Files**:
  `server/smp/src/test/kotlin/com/profiletailors/smp/mcp/security/OAuthFlowIntegrationTest.kt`
- **Description**: End-to-end OAuth flow against a Testcontainers Keycloak + BDD seed: RFC 9728
  discovery → Keycloak authorize (workspace-context JWS) → consent → token exchange →
  workspace-bound `mcp_ping` succeeds.
- **TDD**: RED — flow test fails (no integration glue). GREEN — implement. REFACTOR — extract
  `KeycloakTestFixture` reusable across BDD + integration.
- **Acceptance**: Discovery → token → protected tool call chain succeeds; workspace claim is
  correctly propagated.
- **Verification**: `OAuthFlowIntegrationTest` passes against `just infra-up`.

### Task 29: Workspace isolation test (cross-workspace token rejection)

- **PR / Base**: PR 4 / PR 3
- **Effort**: S
- **Depends on**: T27
- **Files**:
  `server/smp/src/test/kotlin/com/profiletailors/smp/mcp/security/WorkspaceIsolationTest.kt`
- **Description**: Token bound to workspace A cannot read workspace B data. Asserts every tool
  returns `workspace_mismatch` and never reveals B's content.
- **TDD**: RED — isolation test fails (no test). GREEN — implement. REFACTOR — parameterize over all
  4 tools.
- **Acceptance**: Every tool blocks cross-workspace access; no response payload leaks B's entity
  IDs.
- **Verification**: `WorkspaceIsolationTest` passes for all 4 tools.

### Task 30: Contract compatibility test (MCP Inspector + Claude Desktop config)

- **PR / Base**: PR 4 / PR 3
- **Effort**: S
- **Depends on**: T25
- **Files**: `server/smp/src/test/kotlin/com/profiletailors/smp/mcp/McpContractTest.kt`,
  `docs/mcp-server/clients/claude-desktop.json`
- **Description**: Pinned contract test against MCP 1.x `mcp-types.json`. Validates `initialize`,
  `tools/list`, `tools/call` JSON shapes. Plus a runnable Claude Desktop config example driving MCP
  Inspector.
- **TDD**: RED — contract test fails (no fixture). GREEN — pin contract + add inspector recipe.
  REFACTOR — load `mcp-types.json` from `src/test/resources`.
- **Acceptance**: Contract test fails on any schema drift; Claude Desktop config example is
  launchable in MCP Inspector.
- **Verification**: `McpContractTest` passes; manual MCP Inspector run.

### Task 31: `docs/mcp-server.md` with client config examples

- **PR / Base**: PR 4 / PR 3
- **Effort**: M
- **Depends on**: T30
- **Files**: `docs/mcp-server.md`,
  `docs/mcp-server/clients/{claude-desktop.json,cursor.json,chatgpt.json}`
- **Description**: Per AGENTS.md doc rules (Overview → Changes → Usage → Troubleshooting →
  References). Include Client config for Claude Desktop / Cursor / ChatGPT, OAuth flow diagram,
  scope matrix, troubleshooting 401/403/429.
- **TDD**: N/A. Reviewer checks `markdown-a11y` checklist.
- **Acceptance**: Each MVP tool documented with input/output schema; OAuth flow includes consent
  step + workspace selection; troubleshooting covers 401/403/429.
- **Verification**: Doc renders in Astro docs; reviewed against `markdown-a11y`.

### Task 32: Update `.env.example` with `SMP_MCP_*` env vars

- **PR / Base**: PR 4 / PR 3
- **Effort**: XS
- **Depends on**: T25
- **Files**: `.env.example`
- **Description**: Document `SMP_MCP_ENABLED`, `SMP_OAUTH_ISSUER`, `SMP_OAUTH_AUDIENCE_MCP` with
  defaults and short descriptions.
- **TDD**: N/A.
- **Acceptance**: All three vars listed with comment block.
- **Verification**: `grep -E '^SMP_MCP_|^SMP_OAUTH_' .env.example` returns three lines.

### Task 33: Update `RateLimitConfiguration` with MCP buckets

- **PR / Base**: PR 4 / PR 3
- **Effort**: XS
- **Depends on**: T25
- **Files**:
  `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/RateLimitConfiguration.kt`
- **Description**: Register `mcp-channels-read` and `mcp-publications-read` buckets in
  `RateLimitConfiguration`. Wire to `McpProperties.rate-limit.buckets`.
- **TDD**: RED — slice test asserts bucket presence (fails, no buckets). GREEN — add buckets.
  REFACTOR — none.
- **Acceptance**: Buckets are discoverable at startup; capacities come from `McpProperties`.
- **Verification**: `McpRateLimitFilterTest` (PR 3) passes end-to-end.

**PR 4 Verification**: `just ci-local` passes; BDD scenarios pass; `docs/mcp-server.md` is
reviewable; contract test passes.

---

## Explicitly Removed from MVP

These were in earlier drafts and are now scoped OUT:

| Removed item                                                 | Reason                                                                                      |
|--------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| `OAuthMetadataController` (RFC 8414)                         | SMP is NOT an Authorization Server; Keycloak owns `/.well-known/oauth-authorization-server` |
| `ClientRegistrationService`                                  | Keycloak owns `/oauth2/register` (DCR)                                                      |
| `McpToolAuthorizationFilter` as a `WebFilter`                | Replaced by `McpToolInvocationAuthorizer`; security chain must never parse JSON-RPC bodies  |
| `mcp:publications:write` scope                               | Deferred to Phase 5 (write tools)                                                           |
| `password_reset_token` / `refresh_session`                   | Keycloak owns tokens/sessions; SMP persistence is not reused                                |
| `publication_not_found` / `channel_disconnected` error codes | MVP is read-only; no tool takes a publication ID or channel handle                          |
| Single-PR with `size:exception`                              | Forced by user mandate to split into 4 stacked PRs                                          |
| `feature-branch-chain` from `feature/mcp-server`             | Replaced by strict stacked-to-main with rebase after each merge                             |
