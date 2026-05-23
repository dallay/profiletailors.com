# Delta for Platform

## ADDED Requirements

### Requirement: Dedicated Refresh and Logout Endpoints for Local User Sessions

The system MUST expose dedicated local USER session-continuation endpoints separate from protected business APIs.

The refresh flow MUST be available through a dedicated endpoint for session continuation.
The logout flow MUST be available through a dedicated endpoint for authoritative session invalidation.
The refresh endpoint MUST accept refresh-cookie transport without requiring an existing valid access token.
The logout endpoint MUST invalidate the current refresh-backed session when one exists and MUST clear client-facing refresh-cookie state in its response.
The system MUST preserve deny-by-default behavior when refresh or logout requests reference missing or invalid session state.

#### Scenario: Refresh endpoint is public to the session-continuation flow

- GIVEN a browser has no current valid access token in memory
- AND the browser still holds a valid refresh cookie for a local USER session
- WHEN the browser calls the dedicated refresh endpoint
- THEN the platform MUST evaluate the refresh credential without requiring prior protected-API authentication
- AND it MUST issue a new access token only if authoritative refresh state allows it

#### Scenario: Logout invalidates session continuity even after access token loss

- GIVEN a browser has an active refresh-backed local USER session
- WHEN the browser calls the dedicated logout endpoint
- THEN the platform MUST invalidate the authoritative refresh session and clear the refresh cookie
- AND later session bootstrap or retry recovery for that session MUST be denied

## MODIFIED Requirements

### Requirement: Stateless Scaling, Caching, and Invalidation Principles

The system MUST support stateless scaling principles for platform request processing.

Authorization and identity evaluation for API requests MUST be deterministic from explicit request input and current authoritative platform state.
Caches MAY be used to improve performance.
Caches MUST reduce lookup cost without changing authorization semantics.
Cached authorization-related data MUST be invalidated or refreshed when underlying authoritative state changes in a way that could affect effective permissions, scopes, grants, entitlements, workspace membership, or credential validity.
For the implemented service-account bearer path, credential revocation state MUST be treated as authoritative state for protected-request evaluation.
A technically valid presented service-account credential MUST NOT continue to authorize access when current authoritative credential state revokes it.
For the local USER refresh-session path, refresh-credential validity and logout invalidation MUST be treated as authoritative state for session continuation.
A browser that loses in-memory access-token state MAY recover only through current authoritative refresh-session state, not through instance-local memory or durable frontend token persistence.
Caches and stateless nodes MUST NOT preserve a refresh-backed session after authoritative logout, revocation, expiry, or invalidation has occurred.
Caches MUST NOT expand permissions beyond what authoritative state allows.
Phase one MAY use minimal or no cache implementation, but the platform seams MUST permit later safe caching and invalidation.

(Previously: The platform required stateless scaling, cache safety, and authoritative credential-state invalidation for protected-request evaluation, but it did not define refresh-session validity and logout invalidation as authoritative state for local USER session continuation.)

#### Scenario: Invalidated refresh session cannot survive cache or node-local state

- GIVEN a local USER refresh-backed session was previously valid
- AND authoritative backend state later invalidates that refresh session through logout, revocation, or expiry
- WHEN any platform instance evaluates a later refresh attempt for that session
- THEN instance-local state or caches MUST NOT restore the session
- AND the refresh request MUST be denied according to current authoritative state

#### Scenario: Equivalent nodes evaluate refresh continuation consistently

- GIVEN two platform instances evaluate the same refresh request against the same authoritative refresh-session state
- WHEN both instances process the request independently
- THEN they MUST produce the same allow-or-deny refresh outcome
- AND the result MUST NOT depend on which instance previously issued the access token

### Requirement: Deterministic API Protection Principles

The system MUST enforce deny-by-default, explicit-over-implicit, and deterministic API protection behavior.

Protected API behavior MUST require successful authentication, applicable context resolution, and explicit authorization success before access is granted.
The absence of a required permission, grant, membership, or applicable rule MUST result in denial.
Explicit denial MUST override any allow path.
The system MUST NOT infer access from role names, token presence, or unspecified defaults.
Equivalent requests against equivalent state MUST produce equivalent authorization outcomes.
For the existing `/api/authorization/workspace-access/current` proving slice, the same protection principles MUST apply to authenticated USER, authenticated SERVICE_ACCOUNT, and authenticated API_KEY requests.
This change MUST prove end-to-end behavior for that slice with API-key allow, authorization-controlled deny, revoked-or-inactive-credential deny, and completed-replacement cutover outcomes.
For the supported API-key replacement capability, the platform MUST apply one explicit runtime rule: after the replacement operation completes, the successor API key MUST be accepted and the predecessor API key MUST be denied.
The completed replacement rule MUST NOT allow any overlap window where both predecessor and successor are accepted on `/api/authorization/workspace-access/current`.
The API-key replacement proving slice for `/api/authorization/workspace-access/current` MUST remain limited to that endpoint.
The API-key replacement proving slice MUST NOT broaden into new endpoints, service-account rotation, dual-active rollover windows, inventory or detail APIs, or generalized credential-family management.
The API-key replacement proving slice MUST NOT broaden into broad issuance/admin platform behavior beyond what is minimally necessary to execute one API-key replacement path.
For the local USER browser session flow, protected API calls MUST be made with an in-memory access token rather than a durable browser-persisted access token.
For the local USER browser session flow, a `401` from a protected API MAY trigger exactly one refresh-based recovery attempt for the original request.
If that refresh-based recovery attempt fails, the platform and client flow MUST fail closed rather than loop or infer continued access.

(Previously: The platform enforced deterministic protected-API behavior for authenticated requests and API-key replacement outcomes, but it did not define one-time refresh recovery semantics for local USER browser sessions after `401`.)

#### Scenario: Protected request recovers once after access-token expiry

- GIVEN a local USER sends a protected API request with an access token that is no longer accepted
- AND the USER still has a valid refresh-backed session in authoritative backend state
- WHEN the protected request returns `401`
- THEN the client-platform flow MAY perform one refresh-based recovery attempt for that original request
- AND the replayed request MUST be attempted no more than once after a successful refresh

#### Scenario: Protected request fails closed after exhausted recovery

- GIVEN a local USER sends a protected API request that returns `401`
- AND no valid refresh-backed session remains to recover that request
- WHEN the client-platform flow evaluates recovery
- THEN the original request MUST remain denied after the single allowed recovery path is exhausted
- AND the system MUST NOT create an implicit authenticated session from stale client state
