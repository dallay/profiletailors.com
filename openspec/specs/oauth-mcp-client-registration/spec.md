# OAuth MCP Client Registration Specification

## Purpose

Define MCP client discovery and onboarding while Keycloak remains the Authorization Server.

## Requirements

### Requirement: Protected Resource Discovery

SMP MUST publish only RFC 9728 Protected Resource Metadata for `/api/mcp`. It MUST NOT expose
authorization, token, revocation, or authorization-server metadata endpoints. Protected Resource
Metadata MUST identify `https://api.profiletailors.com/api/mcp`, Keycloak's issuer, and MVP scopes
`mcp:channels:read` and `mcp:publications:read`.

#### Scenario: Client discovers Keycloak from a 401

- GIVEN an unauthenticated request to `/api/mcp`
- WHEN SMP rejects authentication
- THEN `401` and `WWW-Authenticate` MUST include a `resource_metadata` URL
- AND that document MUST identify Keycloak as the authorization server

### Requirement: Registration Baseline and DCR

Pre-registered clients MUST be the onboarding baseline. Keycloak MUST support verified RFC 7591
Dynamic Client Registration where supported and enabled. CIMD SHOULD be evaluated separately because
MCP November 2025 recommends it, but unverified CIMD support MUST NOT be claimed or required for
compliance.

#### Scenario: Pre-registered client starts PKCE

- GIVEN a client has a pre-registered identifier and allowed redirect URI
- WHEN it starts Authorization Code with `S256` PKCE
- THEN Keycloak MUST accept the authorization request

#### Scenario: Valid client uses DCR

- GIVEN Keycloak DCR is enabled and valid registration metadata is submitted
- WHEN registration is requested
- THEN Keycloak MUST return a client identifier
- AND the client MUST be able to start Authorization Code with PKCE

#### Scenario: Unsafe redirect URI is rejected

- GIVEN DCR metadata contains an untrusted redirect URI
- WHEN registration is requested
- THEN Keycloak MUST reject it
- AND MUST NOT issue client credentials

### Requirement: Registration Does Not Grant Data Access

Registration MUST NOT grant workspace access or scopes. Access MUST require user authentication, a
workspace-bound grant, current workspace membership, and explicit scope consent.

#### Scenario: Registered client still needs authorization

- GIVEN a registered client has no user authorization grant
- WHEN it calls `/api/mcp`
- THEN SMP MUST reject the request
- AND MUST NOT return workspace data
