package com.profiletailors.smp.mcp.infrastructure.oauth

// T28: OAuth integration test — DEFERRED
//
// A full Keycloak OAuth 2.0 flow test (authorization code + PKCE → token →
// MCP tool call) is deferred until Keycloak is integrated as an identity
// provider. The test would require:
//   - Testcontainers Keycloak (dasniko/testcontainers-keycloak)
//   - Realm configuration with MCP client + scopes
//   - Token exchange flow verification
//
// Current coverage:
//   - McpJwtConverterTest validates JWT → McpAuthenticationToken conversion
//   - McpSecurityConfigurationTest validates 401 / WWW-Authenticate behavior
//   - ResourceMetadataControllerTest validates RFC 9728 endpoint
//   - BDD mcp-tools.feature covers end-to-end HTTP scenarios with test tokens
