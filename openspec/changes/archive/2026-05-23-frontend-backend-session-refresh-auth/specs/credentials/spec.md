# Delta for Credentials

## ADDED Requirements

### Requirement: Refresh Credential Lifecycle for Local User Sessions

The system MUST support a dedicated refresh credential for the local USER authentication flow.

The refresh credential MUST be distinct from the access token used to call protected APIs.
The refresh credential MUST be bound to authoritative backend credential state for one USER
principal.
The backend MUST validate the presented refresh credential against current authoritative state
before issuing a new access token.
The backend MUST persist only the minimum refresh-credential data required to identify the
credential, bind it to the USER principal, determine whether it is active, validate presented
refresh attempts, and invalidate or rotate the credential during logout or refresh.
The backend MUST NOT rely on client-declared identity alone to refresh a session.

#### Scenario: Login issues a refresh credential backed by server state

- GIVEN a local USER authenticates successfully through login or registration
- WHEN the backend completes the authentication flow
- THEN the system MUST create authoritative refresh-credential state bound to that USER
- AND the refresh path MUST be able to validate later refresh requests against that authoritative
  state

#### Scenario: Unknown or invalid refresh credential is denied

- GIVEN a refresh request presents a refresh credential that is missing, unknown, expired, revoked,
  or otherwise not valid in authoritative backend state
- WHEN the backend evaluates the refresh request
- THEN the system MUST deny the refresh request
- AND it MUST NOT issue a new access token

### Requirement: Refresh Credential Transport Uses Secure Cookie Semantics

The system MUST transport the local USER refresh credential through a dedicated cookie rather than
through frontend-managed durable storage.

The refresh cookie MUST be marked `HttpOnly`.
The refresh cookie MUST be marked `SameSite` with an explicit policy appropriate to the app
deployment model.
The refresh cookie MUST be marked `Secure` whenever the effective environment requires secure-cookie
transport.
The refresh endpoint MUST rely on the cookie-backed refresh credential as its input credential
transport.
The frontend MUST NOT persist the refresh credential in `localStorage`, `sessionStorage`, or other
script-readable durable browser storage.

#### Scenario: Refresh cookie is set on successful local authentication

- GIVEN a local USER authenticates successfully through login or registration
- WHEN the backend returns the authentication success response
- THEN the response MUST set the refresh credential in a dedicated cookie with `HttpOnly` semantics
- AND the refresh credential MUST NOT be returned as a script-managed durable token for browser
  storage

#### Scenario: Logout clears the refresh cookie

- GIVEN a local USER has an active refresh-backed session
- WHEN logout completes through the dedicated logout flow
- THEN the backend MUST clear the refresh cookie from the browser-facing response
- AND a later refresh attempt with the cleared session state MUST be denied unless a new login
  occurs

## MODIFIED Requirements

### Requirement: JWT, Service Account, and API Key Platform Concepts

The platform MUST recognize JWT tokens, service accounts, API keys, and refresh credentials for
local USER sessions as first-class credential concepts in the target architecture.

Phase one MUST implement JWT-backed authentication for USER principals in the proving slice.
For the local USER browser session flow, the system MUST issue a short-lived access token for
protected API access and a separate refresh credential for session continuation.
The local USER refresh credential MUST be validated against authoritative backend credential state
before a new access token is issued.
The local USER refresh flow MUST support authoritative invalidation so logout or revocation prevents
later session continuation.
This change MUST additionally implement bearer-based service-account authentication for the existing
`/api/authorization/workspace-access/current` proving slice.
The implemented service-account path MUST validate a presented bearer credential against
authoritative backend credential state before protected access is granted.
The implemented service-account path MUST support authoritative revocation enforcement for that
credential path.
This change MUST additionally implement API-key authentication for the existing
`/api/authorization/workspace-access/current` proving slice only.
For this change, persisted API-key credential state MUST include only the minimum authoritative
fields required to securely identify a credential record for lookup, verify the presented secret
without plaintext or reversible secret persistence, bind the credential to one persisted principal,
determine whether the credential is active or revoked, and make one completed replacement cutover
explicit when predecessor/successor linkage is needed for enforcement.
The implemented API-key path MUST validate a presented API key against authoritative backend
credential state through lookup plus verifier comparison before protected access is granted.
The implemented API-key path MUST support one narrow API-key replacement capability for an existing
active API-key credential on the existing proving slice.
If replacement lineage is needed to make cutover semantics explicit and testable, the system MUST
persist explicit predecessor/successor semantics that relate the replaced API-key credential to its
replacement credential.
When an API-key replacement completes, the successor API key MUST be accepted for subsequent
authentication and the predecessor API key MUST be denied for subsequent authentication.
The replacement cutover MUST NOT permit a dual-active overlap window, grace period, or delayed
predecessor denial.
The implemented API-key path MUST deny a presented API key when authoritative backend credential
state is inactive, revoked, or replaced by a completed successor.
The current change MUST prove before-and-after API-key behavior end to end on
`GET /api/authorization/workspace-access/current`.
Service-account rotation or replacement MUST remain deferred.
Dual-active rollover windows, grace periods, overlap semantics, and delayed predecessor revocation
MUST remain deferred.
Inventory, list, detail, search, or operator-facing management APIs for credentials MUST remain
deferred.
Broad credential issuance or admin CRUD expansion beyond what is minimally necessary to execute one
replacement path MUST remain deferred.
Generalized credential-family management across credential types MUST remain deferred.
Deferred implementation MUST NOT remove any credential concept from the canonical platform model.
The platform SHOULD preserve a path for future external federation or provider-backed token
validation without redefining the core Credentials context.

(Previously: The platform recognized JWT tokens, service accounts, and API keys as first-class
credential concepts in the target architecture, and phase-one USER authentication relied only on
validated JWT behavior without a refresh-credential session-continuation requirement.)

#### Scenario: Valid refresh credential yields a new access token

- GIVEN a local USER has a valid refresh credential in the dedicated cookie
- AND authoritative backend state still marks that refresh credential as valid for that USER session
- WHEN the client calls the dedicated refresh endpoint
- THEN the system MUST issue a new access token for that USER
- AND the refresh credential state MUST remain valid only according to current authoritative backend
  rules

#### Scenario: Logout invalidates the authoritative refresh credential

- GIVEN a local USER has an active refresh credential recognized by authoritative backend state
- WHEN the client calls the dedicated logout endpoint
- THEN the system MUST invalidate that refresh credential in authoritative backend state
- AND a later refresh request for that credential MUST be denied
