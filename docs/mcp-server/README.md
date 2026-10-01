# MCP Server Clients

## Overview

Ready-to-use client configurations for the Profile Tailors MCP server. The server contract itself lives in [MCP Server](../mcp-server.md) and `openspec/specs/mcp-server/`.

## Usage

- Claude Desktop: [claude-desktop.json](./clients/claude-desktop.json) — streamable HTTP transport with OAuth bearer header
- Replace `<YOUR_OAUTH_ACCESS_TOKEN>` with a real OAuth access token; never commit tokens.

## Troubleshooting

- `401` from the MCP endpoint means the bearer token is missing or expired; re-issue OAuth credentials before changing client config.
- Keep transport `streamable-http`; do not downgrade to SSE unless the server contract changes.

## References

- [Docs index](../README.md)
- [MCP Server](../mcp-server.md)
