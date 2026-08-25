# Delta for IAM Platform

## MODIFIED Requirements

### Requirement: Credential Mechanisms
The system MUST support the following authentication mechanisms in phase one:

1. **JWT for USER principals**: Short-lived access tokens for protected API access, obtained through login or refresh. Frontend MUST hold the access token in memory only.
2. **Refresh credentials**: HttpOnly, Secure, SameSite cookie for local USER session continuation. Validated against authoritative backend state. Support authoritative invalidation via logout.
3. **Service-account bearer credentials**: Validated against authoritative backend state. Support revocation enforcement.
4. **API keys**: Validated through lookup + verifier comparison. Support one narrow replacement capability with predecessor/successor semantics and no-overlap cutover.
5. **Email verification gating**: Refresh credential issuance and protected features gated behind `email_status = VERIFIED`.
6. **MCP workspace-bound JWTs**: Keycloak-owned access tokens used at `/api/mcp` MUST have `aud` identifying `https://api.profiletailors.com/api/mcp`, a separate signed `workspace_id` tenant claim, expiry, and only the MVP scopes `mcp:channels:read` and/or `mcp:publications:read`. SMP MUST validate token, issuer, audience, expiry, and `workspace_id` presence, then validate current principal membership in that workspace. Tool scope MUST be enforced at the invocation boundary after Spring AI resolves the tool, not by parsing JSON-RPC in a WebFilter. MCP tokens and sessions MUST NOT use SMP password-reset or refresh-session persistence.

(Previously: Credential mechanisms did not separate the MCP resource audience from tenant identity or define invocation-boundary scope enforcement.)

#### Scenario: JWT materializes an authenticated USER principal
- GIVEN a request includes a valid JWT
- WHEN authenticated
- THEN the USER principal is materialized through repo-local seams

#### Scenario: Logout invalidates refresh credential
- GIVEN an active refresh-backed session
- WHEN logout completes
- THEN the backend invalidates the refresh credential in authoritative state
- AND later refresh attempts are denied

#### Scenario: API key replacement cutover
- GIVEN an active API key is replaced
- WHEN replacement completes
- THEN the successor key is accepted
- AND the predecessor key is denied
- AND no overlap window exists

#### Scenario: Refresh denied for unverified email
- GIVEN a refresh request with valid credential
- AND the user's `email_status` is `UNVERIFIED`
- WHEN the backend evaluates
- THEN the system MUST deny with 403
- AND the error MUST indicate email verification required

#### Scenario: MCP audience and workspace membership are independent
- GIVEN a Keycloak JWT has the MCP audience and `workspace_id=A`
- WHEN SMP validates the request
- THEN it MUST also confirm the principal is a current member of A
- AND MUST deny access if either check fails

#### Scenario: MCP scope is enforced after tool resolution
- GIVEN a valid workspace-bound token lacks the resolved tool's scope
- WHEN `tools/call` reaches the invocation boundary
- THEN the server MUST return `403` with `required_scope`
- AND the tool adapter MUST NOT execute

#### Scenario: MCP JWT cannot cross workspace boundaries
- GIVEN a valid MCP JWT containing `workspace_id=A`
- WHEN the client supplies workspace B in a header or tool argument
- THEN authorization MUST remain bound to workspace A
- AND workspace B data MUST NOT be returned
