# Delta for OAuth Initiation API

## MODIFIED Requirements

### Requirement: LinkedIn OAuth Initiation Endpoint

`POST /api/publishing/{provider}/connections/initiate` MUST return a signed `authorizationUrl` and `state` to an authenticated workspace caller for an available `PERSONAL_PROFILE` provider. The provider path MUST support `THREADS` and retain the existing LinkedIn route as a compatibility alias. The server MUST re-evaluate provider policy before generation, bind provider/workspace/principal/redirect and expiry in tamper-evident state, and use only provider-supported authorization parameters. Threads MUST request only `threads_basic` and `threads_content_publish`; the system MUST NOT invent or require PKCE for Threads.

(Previously: initiation was LinkedIn-only and did not define provider-aware parameter or PKCE behavior.)

#### Scenario: Available Threads provider initiates connection

- GIVEN an authenticated caller, workspace context, and available Threads policy
- WHEN `POST /api/publishing/threads/connections/initiate` is called
- THEN it MUST return 200 with `authorizationUrl` and signed `state`
- AND the URL MUST request only the approved Threads scopes

#### Scenario: LinkedIn alias remains compatible

- GIVEN LinkedIn personal-profile initiation is available
- WHEN the legacy LinkedIn initiation route is called
- THEN it MUST use the provider-aware flow and return the existing response shape

#### Scenario: Policy changed after catalog load

- GIVEN the SPA previously received an available provider entry
- AND current server policy is `LOCKED` or `HIDDEN`
- WHEN initiation is called
- THEN it MUST reject the request without an authorization URL or state

#### Scenario: Missing workspace or authentication is rejected

- GIVEN workspace context or a valid Bearer token is absent
- WHEN initiation is called
- THEN the system MUST reject the request with the existing 400 or 401 behavior

### Requirement: OAuth State Prevents CSRF and Tampering

The `state` parameter generated at initiation MUST encode enough information to validate provider, workspace membership, principal, redirect integrity, and request expiry upon callback. The provider-aware completion endpoint MUST reject any completion request whose state cannot be verified, is expired, or is replayed.

(Previously: state validation protected the LinkedIn flow without requiring provider binding or replay protection.)

#### Scenario: State from another provider is rejected

- GIVEN a valid state generated for LinkedIn
- WHEN it is submitted to Threads completion
- THEN the system MUST reject the request with 400
- AND it MUST NOT persist a connection or account

#### Scenario: Expired or tampered state is rejected

- GIVEN completion receives expired, modified, or replayed state
- WHEN the endpoint validates it
- THEN the system MUST reject the request with 400
- AND the error MUST remain free of state contents or secrets

### Requirement: Safe Failure When Provider Credentials Are Not Configured

If the selected provider OAuth client or redirect configuration is absent or invalid, the provider-aware initiation endpoint MUST fail safely without exposing internal configuration.

(Previously: safe-failure behavior covered only missing LinkedIn configuration.)

#### Scenario: Missing Threads configuration returns clear error

- GIVEN the backend is running without valid Threads OAuth configuration
- WHEN Threads initiation is called
- THEN the system MUST return 503 with a safe provider-not-configured error
- AND it MUST NOT expose stack traces, credentials, or configuration details
