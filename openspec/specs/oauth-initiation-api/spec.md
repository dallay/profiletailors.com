# OAuth Initiation API Specification

## Purpose

Define the authenticated endpoint that builds the LinkedIn authorization URL with a signed state
parameter and returns it for the SPA to redirect the user, enabling the client-side OAuth flow.

## Requirements

### Requirement: LinkedIn OAuth Initiation Endpoint

The system MUST expose `POST /api/publishing/linkedin/connections/initiate` that generates a
LinkedIn authorization URL including a cryptographically signed `state` parameter and returns it to
the requesting SPA.

The endpoint MUST require authentication and an active workspace context. The returned response MUST
include `authorizationUrl` and `state`. The `state` parameter MUST be signed or encrypted to prevent
tampering and MUST be validated on the completion endpoint.

#### Scenario: Authenticated user initiates LinkedIn connection

- GIVEN an authenticated principal and a valid `X-Workspace-Id` header
- WHEN `POST /api/publishing/linkedin/connections/initiate` is called
- THEN the system MUST return 200 with `authorizationUrl` and `state`
- AND `authorizationUrl` MUST include the signed `state` parameter, `client_id`, `redirect_uri`, and
  required `scope`
- AND `state` MUST be tamper-evident (signed or encrypted)

#### Scenario: Initiation without workspace context is rejected

- GIVEN an authenticated principal
- AND no `X-Workspace-Id` header is provided
- WHEN `POST /api/publishing/linkedin/connections/initiate` is called
- THEN the system MUST return 400 with an error indicating workspace context is required

#### Scenario: Unauthenticated initiation is rejected

- GIVEN no valid Bearer token is present
- WHEN `POST /api/publishing/linkedin/connections/initiate` is called
- THEN the system MUST return 401

### Requirement: OAuth State Prevents CSRF and Tampering

The `state` parameter generated at initiation MUST encode enough information to validate workspace
membership and request integrity upon callback. The completion endpoint MUST reject any completion
request whose `state` cannot be verified.

#### Scenario: Tampered state is rejected at completion

- GIVEN a completion request arrives with a `state` value that does not match the signed original
- WHEN the completion endpoint validates `state`
- THEN the system MUST reject the request with 400
- AND it MUST NOT persist any connection or account

#### Scenario: Expired state is rejected at completion

- GIVEN a completion request arrives with a `state` that has exceeded its validity window
- WHEN the completion endpoint validates `state`
- THEN the system MUST reject the request with 400
- AND the error SHOULD indicate the state has expired

### Requirement: Safe Failure When LinkedIn Credentials Are Not Configured

If LinkedIn OAuth client ID or redirect URI is not configured for the active profile, the initiation
endpoint MUST fail safely without exposing internal configuration.

#### Scenario: Missing LinkedIn client configuration returns clear error

- GIVEN the backend is running without LinkedIn OAuth client configuration
- WHEN `POST /api/publishing/linkedin/connections/initiate` is called
- THEN the system MUST return 503 with an error indicating the provider is not configured
- AND it MUST NOT expose stack traces or configuration details
