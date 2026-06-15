# Delta for Identity

## ADDED Requirements

### Requirement: Local User Session Bootstrap and In-Memory Access Token Handling

The system MUST support local USER browser sessions where the access token is held only in client
memory.

The frontend MUST treat the access token as an in-memory session artifact for authenticated API use.
The frontend MUST NOT persist the local USER access token in `localStorage`, `sessionStorage`, or
equivalent durable browser storage.
When the app starts without an in-memory access token, the frontend MUST bootstrap session state by
attempting the dedicated refresh flow before treating a potentially authenticated browser as
anonymous.
If refresh bootstrap succeeds, the frontend MUST continue with the returned authenticated USER
state.
If refresh bootstrap fails, the frontend MUST clear any local authenticated session state and treat
the browser as unauthenticated.

#### Scenario: App startup restores session from refresh when memory is empty

- GIVEN the browser starts the app without an in-memory access token
- AND a valid refresh cookie is still available for the local USER session
- WHEN the frontend performs session bootstrap
- THEN the system MUST call the dedicated refresh flow before protected-user behavior proceeds
- AND the frontend MUST establish authenticated in-memory session state from the refresh result

#### Scenario: App startup fails closed when refresh cannot restore session

- GIVEN the browser starts the app without an in-memory access token
- AND no valid refresh credential is available to restore the local USER session
- WHEN the frontend performs session bootstrap
- THEN the system MUST treat the browser as unauthenticated
- AND the frontend MUST NOT retain stale authenticated state

### Requirement: Local User Access Token Retry Behavior

The system MUST support exactly one automatic access-token recovery attempt for local USER API
requests that fail with `401` due to expired or missing access-token state.

The frontend authenticated request wrapper MUST attach the current in-memory access token when
present.
When a protected request receives `401`, the frontend MAY call the dedicated refresh flow once for
that original request.
If refresh succeeds, the frontend MUST replay the original request exactly one time with the new
access token.
If refresh fails, the frontend MUST fail closed and surface the request failure without additional
refresh loops.
The frontend MUST NOT perform repeated or unbounded automatic refresh retries for a single original
request.

#### Scenario: Single retry succeeds after one refresh

- GIVEN a protected API request is sent with an expired or no-longer-accepted in-memory access token
- AND the local USER still has a valid refresh-backed session
- WHEN the request receives `401`
- THEN the frontend MUST call the dedicated refresh flow once
- AND if refresh succeeds it MUST replay the original request exactly once

#### Scenario: Single retry stops after refresh failure

- GIVEN a protected API request receives `401`
- AND the refresh flow fails because the refresh-backed session is no longer valid
- WHEN the frontend handles the authentication recovery path
- THEN the frontend MUST NOT retry refresh again for that original request
- AND the request MUST fail as unauthenticated or denied

## MODIFIED Requirements

### Requirement: JWT-First Identity Materialization for Phase One

The system MUST support repo-local authenticated principal materialization for the proving slice.

The system MUST derive the authenticated principal identity from a validated credential through
repo-local identity seams.
For USER principals on the proving slice, the system MUST continue to materialize the authenticated
principal from a validated JWT.
For the local USER browser session flow, the frontend MUST obtain that JWT access token through
login or refresh and keep it only in memory for subsequent protected API calls.
For the dedicated refresh endpoint, the backend MUST materialize the same USER principal only after
validating the refresh credential against authoritative backend state and issuing a new JWT for the
session.
For SERVICE_ACCOUNT principals on the proving slice, the system MUST materialize the authenticated
principal from the validated service-account bearer credential path.
For API_KEY principals on the proving slice, the system MUST materialize the authenticated principal
from the validated API-key credential path.
The system MUST treat credential transport as an authentication and principal materialization seam,
not as the source of authorization truth.
The system MUST NOT rely on credential claims or credential presence alone to determine workspace
membership, permission grants, or effective authorization.
Federated social login, external account linking, broader identity lifecycle breadth, and
generalized multi-principal onboarding flows remain deferred.

(Previously: The system supported repo-local authenticated principal materialization for the proving
slice and USER principals were materialized from validated JWTs, but no session-bootstrap or
refresh-driven re-issuance requirement existed for browser sessions.)

#### Scenario: Refresh bootstrap re-establishes the authenticated USER principal

- GIVEN a browser no longer has an in-memory access token for a previously authenticated local USER
- AND the backend still recognizes a valid refresh credential for that USER session
- WHEN the frontend calls the dedicated refresh endpoint during session bootstrap
- THEN the backend MUST issue a new JWT that materializes the same authenticated USER principal
- AND downstream protected API behavior MUST continue to consume the platform principal rather than
  raw cookie state

#### Scenario: Missing or invalid refresh session prevents renewed USER principal establishment

- GIVEN a browser attempts to bootstrap or recover a local USER session through the dedicated
  refresh endpoint
- AND the presented refresh credential is missing, invalid, expired, or revoked in authoritative
  backend state
- WHEN the backend evaluates the refresh request
- THEN the system MUST reject renewed principal establishment for that session
- AND the frontend MUST treat the browser as unauthenticated until a new login occurs
