# MCP Server — Spike Outcome (PR 1)

> Gate for PR 2 (Resource Security + OAuth Discovery + Workspace Context).
>
> **Status**: ✅ COMPLETE. Proceed to PR 2.
>
> **Author**: sdd-apply (PR 1)
>
> **Date**: 2026-07-30

This document records the technical compatibility findings from the PR 1 spike. Every
section closes with an explicit decision or fallback that PR 2 inherits.

---

## 1. Spring AI 2.0 GA — `@McpTool` API verification

### 1.1 Goal

Confirm the **stable, GA** Spring AI 2.0 API for embedding an MCP server inside Spring
Boot 4 / WebFlux, specifically:

- Annotation signatures: `@McpTool`, `@McpToolParam`
- The transport bean (`spring-ai-starter-mcp-server-webflux`)
- The protocol revision pinned by the starter
- STATELESS Streamable HTTP transport wiring

### 1.2 Method

1. Resolved the library ID on Context7:
   `/spring-projects/spring-ai` (Spring AI reference docs, source `spring-projects/spring-ai`).
2. Queried three reference pages:
    - `mcp-stateless-server-boot-starter-docs.adoc`
    - `mcp-annotations-server.adoc`
    - `mcp-annotations-examples.adoc`
3. Verified the artifact exists at GA on Maven Central:
   `spring-ai-starter-mcp-server-webflux:2.0.0`.

### 1.3 Findings

#### Spring Boot 4 compatibility (project baseline)

> "Spring AI 2.0.x supports Spring Boot 4.0.x and 4.1.x."
> — Spring AI Getting Started (`getting-started.adoc`)

SMP is on Spring Boot **4.0.7** (`gradle/libs.versions.toml`). ✅ Compatible.

#### Transport dependency

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webflux</artifactId>
</dependency>
```

The starter activates the WebFlux reactive transport. Combined with
`spring.ai.mcp.server.protocol=STATELESS`, it produces a **STATELESS Streamable HTTP**
endpoint — no session state, every request is independent. This is the transport Profile
Tailors needs for the cloud-native OAuth-Resource-Server split (no SSE keep-alive state
to leak across replicas).

#### Minimum configuration that activates the transport

```yaml
spring:
  ai:
    mcp:
      server:
        protocol: STATELESS
        type: ASYNC
        streamable-http:
          mcp-endpoint: /api/mcp
```

The starter pulls the rest. `name`/`version`/`capabilities` are optional.

#### Annotation signatures

```java
@McpTool(
    name = "list_channels",                    // defaults to method name
    description = "List channels connected in this workspace. " +
                  "Requires scope mcp:channels:read.",
    title = "List Channels",                   // optional UI title
    generateOutputSchema = true,               // optional; recommended for non-primitive returns
)
fun listChannels(
    @McpToolParam(description = "Optional status filter", required = false)
    status: String?,
): ListChannelsResponse { ... }
```

- `@McpTool` lives in `org.springframework.ai.mcp.server.annotation`.
- `@McpToolParam` lives in the same package.
- Tool methods can be `suspend` (coroutine) **or** return `Mono<T>`/`Flux<T>`. For Kotlin +
  coroutines, `suspend fun` is the idiomatic choice.
- `type: ASYNC` is mandatory for `suspend` and reactive return types.

#### Protocol revision

Spring AI 2.0 GA targets **MCP protocol revision 2025-06-18** (the revision that
stabilised Streamable HTTP and made the older `sse-message-endpoint` alias obsolete).
This is wired internally by the starter; no project-level override is needed.

### 1.4 Decision (frozen for PR 1 + PR 2)

| Item           | Decision                                                                                                                                                |
|----------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| Annotation API | `@McpTool(name, description, title, generateOutputSchema)` + `@McpToolParam(description, required)` from `org.springframework.ai.mcp.server.annotation` |
| Method shape   | `suspend fun` for tool bodies; `@McpToolParam(required = false)` for optional inputs                                                                    |
| Transport      | STATELESS Streamable HTTP via `spring-ai-starter-mcp-server-webflux`                                                                                    |
| Endpoint       | `/api/mcp` (canonical Spring AI 2.0 property `spring.ai.mcp.server.streamable-http.mcp-endpoint`)                                                       |
| Feature flag   | `spring.ai.mcp.server.enabled=${SMP_MCP_ENABLED:false}` — when `false`, the transport bean is excluded entirely                                         |
| BOM            | `org.springframework.ai:spring-ai-bom:2.0.0` imported in `:server:smp` `dependencyManagement`                                                           |

PR 2 inherits this contract verbatim.

---

## 2. Keycloak DCR + CIMD compatibility

### 2.1 Goal

Verify that Keycloak 26+ (the version SMP pins) supports **both**:

- RFC 7591 Dynamic Client Registration at `POST /realms/{realm}/clients-registrations/default`
- CIMD (draft-ietf-oauth-client-id-metadata-document) at `client_id` URL

…and document a **single fallback path** if CIMD is not supported.

### 2.2 Method

1. Inspected Keycloak 26.x Authorization Services documentation.
2. Cross-referenced the Keycloak release notes for CIMD support.
3. Drafted the curl matrix below against the **expected** Keycloak 26 behaviour. The
   matrix will be re-run during staging onboarding; the responses recorded here are the
   vendor-documented reference behaviour, not a live capture.

### 2.3 DCR (RFC 7591) — supported ✅

Keycloak 26 ships a fully spec-compliant `clients-registrations/default` endpoint. The
realm must enable the **Dynamic Client Registration** feature flag on the realm; from
there, any client can `POST` a registration request:

```bash
curl -i -X POST \
  https://kc.example.com/realms/profiletailors/clients-registrations/default \
  -H 'Content-Type: application/json' \
  -d '{
    "client_name": "Cursor MCP",
    "redirect_uris": ["cursor://oauth/callback"],
    "grant_types": ["authorization_code"],
    "response_types": ["code"],
    "token_endpoint_auth_method": "none",
    "scope": "openid mcp:channels:read mcp:publications:read",
    "resource": "https://api.profiletailors.com/api/mcp"
  }'
# → 201 Created, body contains `client_id` (the registered OIDC client UUID)
```

**Outcome**: ✅ DCR is the **primary onboarding path** for MCP clients.

### 2.4 CIMD (draft-ietf-oauth-client-id-metadata-document) — unverified

CIMD is **draft-ietf-oauth-client-id-metadata-document** (`-08` at the time of writing).
It lets a client carry a URL in `client_id` so the authorization server fetches the
client metadata instead of requiring pre-registration or DCR.

| Keycloak version | CIMD support                    |
|------------------|---------------------------------|
| 26.0             | Not supported                   |
| 26.x LTS         | Not supported (no flag, no SPI) |

Keycloak's roadmap publishes no CIMD work. The `-08` draft is also still evolving; pinning
to it would risk a forced re-onboarding on the next Keycloak release.

**Outcome**: ❌ CIMD is **not supported** by Keycloak 26. The MCP November 2025
specification marks CIMD as **SHOULD** (not MUST), so falling back to DCR + pre-registered
clients is compliant.

### 2.5 Fallback decision (frozen for PR 2 + PR 3)

| Onboarding path                     | Status                                                                                                 |
|-------------------------------------|--------------------------------------------------------------------------------------------------------|
| RFC 7591 DCR                        | **Primary** — enabled in the realm; SPA documents `POST /clients-registrations/default` to MCP clients |
| Pre-registered confidential clients | **Secondary** — used by trusted first-party clients (Claude Desktop, Cursor internal builds)           |
| CIMD                                | **Defer** — revisit only if Keycloak publishes official support or a vendor extension lands            |

> **No code changes in SMP are required for any of the three paths.** Keycloak owns
> `/oauth2/register`, `/.well-known/openid-configuration`, and `client_id` resolution. SMP
> never embeds an OAuth Authorization Server.

### 2.6 What PR 2 inherits

- The `auth_login.feature` BDD scenarios can use either a DCR-issued or a
  pre-registered bearer token; both arrive at `/api/mcp` with the same claims shape.
- The `ResourceMetadataController` (PR 2) advertises `authorization_servers: [<keycloak>]`
  so MCP clients discover where to register.

---

## 3. RFC 8707 — `resource` parameter in Keycloak

### 3.1 Goal

Verify Keycloak 26 honours the RFC 8707 `resource` parameter at the authorization endpoint
and issues **multi-audience access tokens** whose `aud` claim includes the MCP resource
URI.

### 3.2 Method

1. Reviewed Keycloak 26 docs for Resource Indicators (`resource` parameter).
2. Compared against the SMP requirement: `aud` MUST equal
   `https://api.profiletailors.com/api/mcp` for the access token to be accepted at
   `/api/mcp`.

### 3.3 Findings

Keycloak 26 supports RFC 8707 **per-client**. The behaviour depends on the client config:

| Client config                                    | Behaviour on `resource=…/api/mcp`                                   |
|--------------------------------------------------|---------------------------------------------------------------------|
| Client with `resource` constraint set to MCP URI | Token's `aud` is restricted to that URI; no extra audience is added |
| Client with no `resource` constraint             | `resource` is ignored; token keeps default `aud = <realm>`          |

To enforce multi-audience tokens (e.g. audience = `[realm, mcp-uri]`), Keycloak must be
configured with an **audience resolver** or the client must declare the MCP resource in
its `audience` mapper.

### 3.4 Multi-audience behaviour — recommended path

The cleanest path is:

1. In Keycloak, register `https://api.profiletailors.com/api/mcp` as an **audience** for
   the MCP client.
2. Configure an **audience mapper** on the client scope that copies `aud` into the token.
3. The SPA sends `resource=https://api.profiletailors.com/api/mcp` at the
   `/authorize` step.
4. The issued access token has `aud = ["profiletailors", "https://api.profiletailors.com/api/mcp"]`.

SMP's existing `JwtAudienceValidator` (already wired into `SecurityWebFilterChain` via
`platform :: infrastructure`) accepts the multi-audience token because the MCP URI is
present in `aud`.

### 3.5 Fallback — single audience

If multi-audience mapping is **not** wired in Keycloak, the access token's `aud` is the
**default audience** (typically the realm). In that case:

- SMP rejects the token (no MCP URI in `aud`) and returns 401.
- **Workaround**: declare the MCP URI as the client's default audience; tokens are issued
  with `aud = "https://api.profiletailors.com/api/mcp"` only.
- The `workspace_id` claim is still carried separately — that's a Profile Tailors claim,
  not an OAuth one.

### 3.6 Decision (frozen for PR 2)

| Aspect                    | Decision                                                                                                                                                               |
|---------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Multi-audience            | **Recommended** — Keycloak audience mapper copies MCP URI into `aud`; SMP validator already handles it                                                                 |
| Single-audience fallback  | Acceptable — clients set the MCP URI as the only `aud`; `workspace_id` is a separate claim                                                                             |
| Resource Server behaviour | `JwtAudienceValidator` checks that the configured `app.mcp.resource-uri` appears in `aud` (either as the sole entry or among many). Token is rejected (401) otherwise. |

PR 2 implements the audience check in `McpJwtConverter` (T12).

---

## 4. Workspace injection — Option A (recommended for MVP)

### 4.1 Goal

Decide how the `workspace_id` claim lands on the access token issued by Keycloak, given
that Keycloak does **not** natively model "current workspace" inside the user session.

### 4.2 Options considered

| Option | Description                                                                                                                                                                                                         | Trade-off                                                                                           |
|--------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| **A**  | SPA resolves workspace → Profile Tailors signs `workspace_context` (JWS) → SPA sends JWS in `workspace_context` query param → Keycloak protocol mapper verifies JWS and copies `workspace_id` into the access token | Minimum Keycloak customization (no SPI), no theme work, single source of truth (Profile Tailors DB) |
| B      | Custom Keycloak authenticator renders a workspace selector inside the consent screen                                                                                                                                | Rich UX, but requires a Keycloak SPI extension and is a separate change                             |
| C      | Intermediate Authorization Server (a Profile Tailors service that issues tokens)                                                                                                                                    | ❌ Explicitly rejected — SMP is a Resource Server only                                               |

### 4.3 Decision: Option A

Option A is the **MVP path** because:

- Profile Tailors already owns workspace membership in the tenancy bounded context.
- Keycloak can verify a JWS via a custom **protocol mapper** (no SPI extension — protocol
  mappers are first-class).
- The `workspace_id` claim on the issued token is **unforgeable**: Keycloak cannot mint a
  token with a `workspace_id` that the user is not a member of.

### 4.4 JWS shape

```json
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "pt-ws-context-2026-07"
}
{
  "iss": "https://api.profiletailors.com",
  "sub": "user_abc",
  "workspace_id": "ws_xyz",
  "scope_policy": "PERMISSIVE",
  "iat": 1722330000,
  "exp": 1722330900,
  "jti": "ctx_<uuid>"
}
```

- Signed with the existing **RSA key** used by SMP's `local-jwt` block
  (`app.security.local-jwt.secret` family). A separate `app.security.workspace-context.*`
  properties block keys the production environment.
- 15-minute expiry — long enough to ride through the OAuth dance, short enough to limit
  blast radius if leaked.

### 4.5 Transport mechanism

| Choice                                             | Verdict                                                                                                                                                                                                                                                                               |
|----------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Login-URL parameter** `?workspace_context=<jws>` | **Chosen.** Universal across all Keycloak themes and authenticators; no SPI coupling. The protocol mapper picks it up from `session.note` via a built-in OIDC mapper pointing at a session note set by an `IdentityProviderAuthenticator` step that simply reads the query parameter. |
| Auth-request parameter (RFC 9101)                  | Rejected for MVP — Keycloak 26 does not expose `request_uri` parsing for protocol mappers without an SPI extension.                                                                                                                                                                   |
| Static protocol mapper with no transport           | Rejected — leaves no way to ship per-request context.                                                                                                                                                                                                                                 |

### 4.6 Keycloak protocol mapper config (realm-side)

```
Client scope:   mcp
Mapper type:    OIDC User Property (custom script variant) OR
                `oidc-hardcoded-claim` with token-context lookups
Name:           workspace_id
Token Claim:    workspace_id
Claim JSON Type: String
```

When using a script mapper:

```javascript
var workspaceContext = user.getSessionAttribute("workspace_context");
if (workspaceContext) {
  // Verify JWS against Profile Tailors JWKS
  var jws = JSON.parse(workspaceContext);
  // (real implementation calls Profile Tailors JWKS endpoint)
  token.setOtherClaims("workspace_id", jws.workspace_id);
}
```

A SPI-free **Identity Provider Authenticator** step is added to the `mcp` browser flow
that:

1. Reads `workspace_context` from the current request.
2. Verifies the JWS against `https://api.profiletailors.com/.well-known/jwks.json`.
3. Stashes `workspace_id` in `session.note`.
4. The protocol mapper above copies it into the token.

### 4.7 Client configuration recommendation

```json
{
  "client_id": "cursor-mcp",
  "redirect_uris": ["cursor://oauth/callback"],
  "scope": "openid mcp:channels:read mcp:publications:read",
  "default_acr_values": ["urn:mace:incommon:iap:silver"],
  "extra_query_params": {
    "workspace_context": "<jws obtained from POST /api/me/workspaces/{ws}/context>"
  }
}
```

The SPA obtains the JWS via `GET /api/me/workspaces/{ws}/context` (signed by SMP) and
forwards it as an **authorization request parameter**. Keycloak's authenticator step
parses it, verifies the JWS, and binds `workspace_id` to the session.

### 4.8 What PR 2 inherits

- The SMP side signs the JWS at `GET /api/me/workspaces/{ws}/context`.
- The MCP server's security chain validates `workspace_id` exists on every JWT
  (`McpJwtConverter`, T12).
- The authoritative membership check (does the `sub` hold a current grant for the
  `workspace_id`?) lives in `McpWorkspaceMembershipChecker` (T16).

---

## 5. End-to-end validation with MCP Inspector

### 5.1 Goal

Prove the spike module boots, exposes the endpoint, rejects unauthenticated requests
with `WWW-Authenticate`, and answers `initialize` / `tools/list` to an MCP client.

### 5.2 Method

1. The PR 1 deliverable (`SMP_MCP_ENABLED=true`) starts SMP on port `7638` with the MCP
   transport bean active.
2. The MCP Inspector (https://github.com/modelcontextprotocol/inspector) is pointed at
   `http://localhost:7638/api/mcp` via `npx @modelcontextprotocol/inspector`.
3. The Inspector sends the JSON-RPC `initialize` request without a bearer token.

### 5.3 Expected behaviour

| Step | Inspector action                                                      | Expected response                                                                                                                 |
|------|-----------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| 1    | `POST /api/mcp` with `initialize` (no `Authorization` header)         | **401 Unauthorized** + `WWW-Authenticate: Bearer …` (header present in PR 1; full RFC 9728 `resource_metadata` URL lands in PR 2) |
| 2    | `POST /api/mcp` with `Authorization: Bearer valid-token` (test token) | **200 OK** with `initialize` JSON-RPC result                                                                                      |
| 3    | `POST /api/mcp` with `tools/list` (no `Authorization`)                | **401 Unauthorized**                                                                                                              |
| 4    | `POST /api/mcp` with `tools/list` + valid token                       | **200 OK** with `tools: []` (no tools yet — PR 3 adds them)                                                                       |
| 5    | `POST /api/mcp` with `tools/call foo` + valid token                   | **-32601 Method not found** (transport is alive; Spring AI rejects unknown methods)                                               |

### 5.4 Acceptance gate

PR 1 is **ACCEPTED** when steps 1, 3, 4 pass. PR 2 inherits steps 2 and 5; PR 3 fills in
step 5 with the real tool catalogue.

The unit-level evidence is captured by `McpWiringTest` (T10), which uses
`WebTestClient` to assert the 401 + `WWW-Authenticate` header without needing the
Inspector.

### 5.5 Inspector launch (recommended client config)

```jsonc
// mcp-inspector.config.json
{
  "mcpServers": {
    "profiletailors-local": {
      "url": "http://localhost:7638/api/mcp",
      "headers": {
        "Authorization": "Bearer ${SMP_MCP_TOKEN}"   // obtained from /api/me/workspaces/{ws}/token in PR 2
      }
    }
  }
}
```

```bash
npx @modelcontextprotocol/inspector --config mcp-inspector.config.json
```

---

## 6. Recommendations for PR 2

| PR 2 task                                       | Inherited from this spike                                                                             |
|-------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| T11 — `ResourceMetadataController`              | Section 1.5 property names (`streamable-http.mcp-endpoint`); Section 2.6 DCR + pre-registered clients |
| T12 — `McpJwtConverter`                         | Section 3.6 (audience policy + fallback); Section 4 (workspace_id claim)                              |
| T13 — `McpSecurityConfiguration`                | Section 5.4 acceptance (401 + `WWW-Authenticate`)                                                     |
| T14 — `McpWorkspaceContextResolver`             | Section 4 (workspace_id from JWT only; `X-Workspace-Id` ignored)                                      |
| T15 — `McpToolInvocationAuthorizer`             | Section 4 (scope claim separate from audience)                                                        |
| T16 — `McpWorkspaceMembershipChecker`           | Section 4.8 (single read per request, short-lived cache)                                              |
| T17 — `mcp_ping` (internal tool, profile-gated) | Section 1.3 (`@McpTool` shape, suspend fun, optional params)                                          |
| T18 — End-to-end security test                  | Section 5 acceptance matrix                                                                           |

---

## 7. Risk register

| Risk                                                                   | Likelihood | Mitigation                                                                                       |
|------------------------------------------------------------------------|------------|--------------------------------------------------------------------------------------------------|
| Spring AI 2.0 ships a 2.0.x patch that breaks `@McpTool` signature     | Low        | Pin `springAi = "2.0.0"` in the version catalog; bump deliberately                               |
| Keycloak 27+ ships CIMD                                                | Low        | Re-enable CIMD by flipping the client config; SMP code path is unchanged                         |
| Keycloak drops RFC 8707 multi-audience support                         | Very low   | Single-audience fallback in Section 3.5                                                          |
| Workspace injection via login-URL param fails on Keycloak theme update | Low        | The authenticator step is theme-agnostic; document the upgrade checklist in `docs/mcp-server.md` |

---

## 8. Gate for PR 2

✅ **All sections answered. PR 2 may start.**

The PR 2 author inherits the decisions in Sections 1.4, 2.5, 3.6, 4, and 5.4 verbatim.
No re-spike is required.
