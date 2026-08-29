# MCP Server

## Overview

Profile Tailors exposes a [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) server
at `POST /api/mcp` using JSON-RPC 2.0 over Streamable HTTP transport. AI clients (Claude Desktop,
Cursor, ChatGPT plugins) can list connected channels, query publications, view calendar data, and
browse provider catalogs — all scoped to a single workspace via OAuth 2.0 JWT tokens.

The server is gated behind `SMP_MCP_ENABLED=true` and requires Spring AI MCP auto-configuration.

## Changes

| PR  | Scope                                                                                  |
| --- | -------------------------------------------------------------------------------------- |
| 1   | Foundation: bounded context, feature flag, transport wiring                            |
| 2   | Security: JWT converter, RFC 9728 metadata, presence filter                            |
| 3   | Tools: 4 read adapters, error mapper, audit facts, rate limiter                        |
| 4   | Verification: BDD scenarios, tests, documentation                                      |
| 5   | Reachable read tools + `mcp_ping` health check (DALLAY-590 unit 1)                    |
| 6   | Write tools: `create_publication`, `edit_publication`, `delete_publication` (DALLAY-590 unit 2) |
| 7   | Write tools: `cancel_publication`, `retry_publication` + BDD + final gates (DALLAY-590 unit 3) |

The unit 1 rewire fixed the gap left by DALLAY-434: the four read tools are now `@McpTool`-annotated and visible through `tools/list`, `McpWorkspaceMembershipChecker` runs a real tenancy query (fail-closed), `McpToolInvocationAuthorizer` enforces per-tool scope, and `McpAuditEmitter` publishes a fact per call to the `mcp.audit` logger. `McpErrorMapper` carries the write error codes; `McpRateLimitFilter` adds the `mcp-publications-write` bucket; `IdempotencyGuard` persists hashed keys under `idempotency_records`. Units 2 and 3 register the five write tools and add `cancel_publication` and `retry_publication`. The contract is fixed by [ADR-0019](architecture/adr/0019-mcp-write-tools.md).

The five write tools and their accompanying scope are designed in
[ADR-0019](architecture/adr/0019-mcp-write-tools.md) and arrive across units 2 and 3.

## Usage

### Available Tools

#### Read tools (live as of unit 1)

| Tool               | Scope Required           | Rate Limit         | Description                          |
| ------------------ | ------------------------ | ------------------ | ------------------------------------ |
| `mcp_ping`         | _(no scope required)_    | _(no bucket)_      | Health check: server time, feature flag, protocol version |
| `list_channels`    | `mcp:channels:read`      | 60 req/min/ws      | List connected social media channels |
| `list_publications`| `mcp:publications:read`  | 30 req/min/ws      | List publications in a date range    |
| `get_calendar`     | `mcp:publications:read`  | 30 req/min/ws      | Calendar view of publications        |
| `list_providers`   | `mcp:providers:read`     | 30 req/min/ws      | List available social providers and per-workspace quota/remaining connections |

#### Write tools (live as of unit 3)

| Tool                  | Scope Required           | Rate Limit         | Hints                                              | Status     |
| --------------------- | ------------------------ | ------------------ | -------------------------------------------------- | ---------- |
| `create_publication`  | `mcp:publications:write` | 15 req/min/ws      | `destructiveHint`, `openWorldHint`                 | Unit 2     |
| `edit_publication`    | `mcp:publications:write` | 15 req/min/ws      | `destructiveHint`, `openWorldHint`                 | Unit 2     |
| `delete_publication`  | `mcp:publications:write` | 15 req/min/ws      | `destructiveHint`, `idempotentHint`, `openWorldHint` | Unit 2   |
| `cancel_publication`  | `mcp:publications:write` | 15 req/min/ws      | `destructiveHint`, `idempotentHint`, `openWorldHint` | Unit 3   |
| `retry_publication`   | `mcp:publications:write` | 15 req/min/ws      | `destructiveHint`, `openWorldHint`                 | Unit 3     |

All five write tools accept an optional `idempotencyKey: String` (1–128 chars, opaque). A repeat
call with the same key in the same `(workspace, principal)` MUST return the cached
`PublicationResult` without re-running the underlying command.

### OAuth 2.0 Flow

```
┌──────────┐     ┌──────────────────┐     ┌─────────────────┐
│ AI Client│     │ Authorization    │     │ MCP Server      │
│ (Claude) │     │ Server (Keycloak)│     │ /api/mcp        │
└────┬─────┘     └────────┬─────────┘     └────────┬────────┘
     │                    │                        │
     │  1. GET /.well-known/oauth-protected-resource/api/mcp
     │────────────────────────────────────────────>│
     │  { resource, authorization_servers,         │
     │    scopes_supported }                       │
     │<────────────────────────────────────────────│
     │                    │                        │
     │  2. Authorization Code + PKCE               │
     │───────────────────>│                        │
     │  access_token (aud: api/mcp,                │
     │   workspace_id, scope: mcp:*)               │
     │<───────────────────│                        │
     │                    │                        │
     │  3. POST /api/mcp                           │
     │   Authorization: Bearer <token>             │
     │────────────────────────────────────────────>│
     │  JSON-RPC response                          │
     │<────────────────────────────────────────────│
```

### Scope Matrix

| Scope                   | Tools                                                                                  |
| ----------------------- | -------------------------------------------------------------------------------------- |
| _(no scope)_            | `mcp_ping` (authentication + workspace membership are still required)                  |
| `mcp:channels:read`     | `list_channels`                                                                        |
| `mcp:publications:read` | `list_publications`, `get_calendar`                                                    |
| `mcp:providers:read`    | `list_providers`                                                                       |
| `mcp:publications:write`| `create_publication`, `edit_publication`, `delete_publication`, `cancel_publication`, `retry_publication` (units 2–3) |

A token carrying `mcp:publications:write` SHOULD also carry `mcp:publications:read` so the agent
can inspect its own writes through `list_publications`; this is a Keycloak client-scope grant,
not a code change.

### Error Catalogue

| Code                            | Category        | Trigger                                                                            |
| ------------------------------- | --------------- | ---------------------------------------------------------------------------------- |
| `insufficient_scope`            | authorization   | Token missing the per-tool scope; consult §Scope Matrix.                          |
| `forbidden`                     | authorization   | Spring Security denied; generally workspace binding failure.                      |
| `publication_not_found`         | not_found       | Write tool called against an unknown `publicationId`.                             |
| `publication_state_conflict`    | validation      | Operation illegal in current publication state (e.g. retry on `CANCELLED`).       |
| `publication_validation_failed` | validation      | Domain rejected the request (missing title, body, asset, etc.).                    |
| `media_unavailable`             | platform        | Referenced `assetId` not reachable by the platform. Retryable.                     |
| `idempotency_conflict`          | idempotency     | Same `idempotencyKey` reused with a different payload in the same workspace.       |
| `invalid_date_range`            | validation      | `from` / `to` are not ISO-8601 instants or `from > to`.                            |
| `rate_limit_exceeded`           | throttling      | Bucket exhausted; check `Retry-After` and the §Available Tools rate-limit column.  |
| `internal`                      | internal        | Unexpected failure; the agent SHOULD retry with backoff.                           |

### Client Configuration

#### Claude Desktop

```json
{
  "mcpServers": {
    "profile-tailors": {
      "url": "https://api.profiletailors.com/api/mcp",
      "transport": "streamable-http",
      "headers": {
        "Authorization": "Bearer <YOUR_OAUTH_ACCESS_TOKEN>"
      }
    }
  }
}
```

See [`docs/mcp-server/clients/claude-desktop.json`](mcp-server/clients/claude-desktop.json) for a
copy-paste template.

#### Cursor

In Cursor Settings → MCP Servers, add:

- **Name**: `profile-tailors`
- **URL**: `https://api.profiletailors.com/api/mcp`
- **Transport**: Streamable HTTP
- **Auth**: Bearer token (paste your OAuth access token)

#### ChatGPT (Plugin / Action)

Point the OpenAPI action endpoint to `POST /api/mcp` with the Bearer token in headers.

## Troubleshooting

### 401 Unauthorized

| Symptom                                | Cause                                   | Fix                                                |
| -------------------------------------- | --------------------------------------- | -------------------------------------------------- |
| Missing `Authorization` header         | No Bearer token sent                    | Add `Authorization: Bearer <token>` header         |
| `WWW-Authenticate: Bearer realm="mcp"` | Token missing or malformed              | Verify token format and audience claim             |
| `JWT audience does not contain ...`    | Token audience mismatch                 | Request token with `aud: https://api.profiletailors.com/api/mcp` |
| `JWT is missing required claim`        | Missing `workspace_id` in token         | Ensure authorization server includes `workspace_id` claim |

### 403 Forbidden

| Symptom           | Cause                        | Fix                                                                                  |
| ----------------- | ---------------------------- | ------------------------------------------------------------------------------------ |
| `access_denied`   | Missing required MCP scope   | Request token with the scope required by the tool (see §Scope Matrix)                |
| `insufficient_scope` | Per-tool scope denied      | Token is missing the scope registered for that tool; cross-check §Scope Matrix     |
| `idempotency_conflict` | Same `idempotencyKey` reused with a different payload | Use a fresh `idempotencyKey` or align the new payload with the original |

### 429 Rate Limited

| Symptom               | Cause                            | Fix                           |
| --------------------- | -------------------------------- | ----------------------------- |
| `rate_limit_exceeded`  | Exceeded bucket limit            | Wait 60 seconds and retry     |
| `mcp-channels-read`   | > 60 requests/min/workspace      | Reduce request frequency      |
| `mcp-publications-read`| > 30 requests/min/workspace     | Batch or cache results        |
| `mcp-publications-write`| > 15 requests/min/workspace    | Batch or cache writes; supply `idempotencyKey` so retries are safe |

## References

- [MCP Protocol Specification (2025-03-26)](https://modelcontextprotocol.io/specification/2025-03-26)
- [RFC 9728 — OAuth 2.0 Protected Resource Metadata](https://www.rfc-editor.org/rfc/rfc9728.html)
- [Spring AI MCP Server](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html)
- [Profile Tailors API Standards](api-versioning.md)
- [ADR-0002 — Adhere to Hexagonal Architecture](architecture/adr/0002-adhere-to-hexagonal-architecture.md)
- [ADR-0008 — Application-Level Multi-tenancy](architecture/adr/0008-application-level-multi-tenancy.md)
- [ADR-0019 — MCP Write Tools for Publication Lifecycle](architecture/adr/0019-mcp-write-tools.md)
