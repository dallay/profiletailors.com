# MCP Client Configs

## Overview

Machine-readable client configs. Human contract lives in [MCP Server](../../mcp-server.md).

## Usage

- [claude-desktop.json](./claude-desktop.json) — Claude Desktop `streamable-http` config

## Troubleshooting

- Never commit real tokens; the file ships with `<YOUR_OAUTH_ACCESS_TOKEN>` as placeholder.
- If auth fails, re-issue OAuth credentials before changing transport.

## References

- [MCP Server](../../mcp-server.md)
- [MCP Clients README](../README.md)
