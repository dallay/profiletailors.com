# OAuth Initiation API Specification

## Purpose

Define the authenticated endpoint that builds the LinkedIn authorization URL with a signed state
parameter and returns it for the SPA to redirect the user, enabling the client-side OAuth flow.

## Requirements

### Requirement: LinkedIn OAuth Initiation Endpoint

`POST /api/publishing/linkedin/connections/initiate` MUST return signed `authorizationUrl` and `state` to an authenticated workspace caller. Before generation, the server MUST re-evaluate LinkedIn personal-profile policy. It MUST deny initiation unless `AVAILABLE`; the client catalog MUST NOT authorize. State MUST remain tamper-evident and validatable at completion.

#### Scenario: Available provider initiates connection
- GIVEN an authenticated caller, workspace context, and `AVAILABLE` policy
- WHEN initiation is called
- THEN it MUST return 200 with `authorizationUrl` and signed `state`
- AND the URL MUST include required parameters

#### Scenario: Policy changed after catalog load
- GIVEN the SPA previously received `AVAILABLE`
- AND current server policy is `LOCKED` or `HIDDEN`
- WHEN initiation is called
- THEN it MUST reject the request without an authorization URL or state

#### Scenario: Missing workspace or authentication is rejected
- GIVEN workspace context or a valid Bearer token is absent
- WHEN initiation is called
- THEN the system MUST reject the request with the existing 400 or 401 behavior

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
