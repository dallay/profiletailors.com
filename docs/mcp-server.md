# MCP Server

## Overview

Profile Tailors exposes a [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) server
at `POST /api/mcp` using JSON-RPC 2.0 over Streamable HTTP transport. AI clients (Claude Desktop,
Cursor, ChatGPT plugins) can list connected channels, query publications, view calendar data, and
browse provider catalogs — all scoped to a single workspace via OAuth 2.0 JWT tokens.

The server is gated behind `SMP_MCP_ENABLED=true` and requires Spring AI MCP auto-configuration.

## Changes

| PR | Scope                                                       |
|----|-------------------------------------------------------------|
| 1  | Foundation: bounded context, feature flag, transport wiring |
| 2  | Security: JWT converter, RFC 9728 metadata, presence filter |
| 3  | Tools: 4 adapters, error mapper, audit facts, rate limiter  |
| 4  | Verification: BDD scenarios, tests, documentation (this PR) |

## Usage

### Available Tools

| Tool                | Scope Required          | Rate Limit    | Description                          |
|---------------------|-------------------------|---------------|--------------------------------------|
| `list_channels`     | `mcp:channels:read`     | 60 req/min/ws | List connected social media channels |
| `list_publications` | `mcp:publications:read` | 30 req/min/ws | List publications in a date range    |
| `get_calendar`      | `mcp:publications:read` | 30 req/min/ws | Calendar view of publications        |
| `list_providers`    | `mcp:publications:read` | 30 req/min/ws | List available social providers      |

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

| Scope                   | Tools                                                 |
|-------------------------|-------------------------------------------------------|
| `mcp:channels:read`     | `list_channels`                                       |
| `mcp:publications:read` | `list_publications`, `get_calendar`, `list_providers` |

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

| Symptom                                | Cause                           | Fix                                                              |
|----------------------------------------|---------------------------------|------------------------------------------------------------------|
| Missing `Authorization` header         | No Bearer token sent            | Add `Authorization: Bearer <token>` header                       |
| `WWW-Authenticate: Bearer realm="mcp"` | Token missing or malformed      | Verify token format and audience claim                           |
| `JWT audience does not contain ...`    | Token audience mismatch         | Request token with `aud: https://api.profiletailors.com/api/mcp` |
| `JWT is missing required claim`        | Missing `workspace_id` in token | Ensure authorization server includes `workspace_id` claim        |

### 403 Forbidden

| Symptom         | Cause                      | Fix                                                               |
|-----------------|----------------------------|-------------------------------------------------------------------|
| `access_denied` | Missing required MCP scope | Request token with `mcp:channels:read` or `mcp:publications:read` |

### 429 Rate Limited

| Symptom                 | Cause                       | Fix                       |
|-------------------------|-----------------------------|---------------------------|
| `rate_limit_exceeded`   | Exceeded bucket limit       | Wait 60 seconds and retry |
| `mcp-channels-read`     | > 60 requests/min/workspace | Reduce request frequency  |
| `mcp-publications-read` | > 30 requests/min/workspace | Batch or cache results    |

## References

- [MCP Protocol Specification (2025-03-26)](https://modelcontextprotocol.io/specification/2025-03-26)
- [RFC 9728 — OAuth 2.0 Protected Resource Metadata](https://www.rfc-editor.org/rfc/rfc9728.html)
- [Spring AI MCP Server](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html)
- [Profile Tailors API Standards](api-versioning.md)
