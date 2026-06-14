# Credentials Specification

## Purpose

Define credential and token semantics for the reusable IAM platform. This specification establishes
the separation between principal identity and credential forms, plus the platform concepts for JWTs,
service accounts, API keys, and related credential paths while keeping phase-one implementation
intentionally narrow.

## Requirements

### Requirement: Credential Concepts Are Distinct from Principal and Authorization Models

The system MUST model credential concepts separately from principal identity and authorization
semantics.

Credentials MUST describe how a principal authenticates.
Credentials MUST NOT by themselves define workspace membership or effective authorization.
The Credentials context MUST preserve room for JWTs, service account credentials, API keys, and
future provider-backed credentials within a stable platform model.

#### Scenario: Credential form does not decide authorization alone

- GIVEN a principal authenticates successfully with a supported credential form
- WHEN the principal requests a protected capability
- THEN the platform MUST require downstream authorization evaluation beyond credential validation
- AND credential success alone MUST NOT grant access

#### Scenario: New credential forms fit the stable model

- GIVEN the platform later adds a new supported credential form
- WHEN the credential model is extended
- THEN the extension MUST fit within the stable Credentials context semantics
- AND the principal and authorization models MUST remain intact

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

#### Scenario: JWT supports the phase-one proving slice

- GIVEN a phase-one protected request includes a valid JWT
- WHEN the credential is validated successfully
- THEN the platform MUST use that JWT path to authenticate the request
- AND the proving slice MAY continue into identity and authorization evaluation

#### Scenario: Service-account bearer credential is validated for the proving slice

- GIVEN a protected request to `/api/authorization/workspace-access/current` presents a bearer
  credential for a persisted service account
- WHEN the credential is validated successfully against authoritative backend credential state
- THEN the platform MUST treat the request as authenticated through the service-account credential
  path
- AND the proving slice MAY continue into identity and authorization evaluation

#### Scenario: Revoked service-account credential is denied before protected access

- GIVEN a protected request to `/api/authorization/workspace-access/current` presents a bearer
  credential that is otherwise structurally valid for a persisted service account
- AND authoritative backend credential state marks that credential as revoked
- WHEN the platform evaluates authentication for the request
- THEN the platform MUST reject the request as unauthenticated or invalid for the protected slice
- AND the protected use case MUST NOT execute

#### Scenario: Predecessor API key is accepted before replacement completes

- GIVEN a persisted API-key credential is active for a principal allowed to access
  `/api/authorization/workspace-access/current`
- AND no completed replacement has made that credential a predecessor
- WHEN the platform validates a request that presents that API key
- THEN the platform MUST authenticate the request through the API-key credential path
- AND the proving slice MAY continue into identity and authorization evaluation

#### Scenario: Successor API key is accepted after replacement completes

- GIVEN an existing active API-key credential has been replaced through the supported replacement
  capability
- AND a successor API-key credential is explicitly linked as the completed replacement for that
  predecessor
- WHEN a request to `/api/authorization/workspace-access/current` presents the successor API key
- THEN the platform MUST authenticate the request through the API-key credential path
- AND the proving slice MAY continue into identity and authorization evaluation

#### Scenario: Predecessor API key is denied after replacement completes

- GIVEN an existing active API-key credential has been replaced through the supported replacement
  capability
- AND a successor API-key credential is explicitly linked as the completed replacement for that
  predecessor
- WHEN a request to `/api/authorization/workspace-access/current` presents the predecessor API key
- THEN the platform MUST reject the request as unauthenticated or invalid for the protected slice
- AND the protected use case MUST NOT execute

#### Scenario: Dual-active overlap is out of scope for replacement cutover

- GIVEN a requested credential lifecycle behavior requires a period where predecessor and successor
  API keys are both valid
- WHEN the replacement scope for this change is evaluated
- THEN the platform MUST treat that behavior as deferred
- AND the supported replacement capability MUST keep a no-overlap cutover rule

#### Scenario: Service-account rotation remains deferred

- GIVEN a requested capability requires service-account credential replacement or rotation
- WHEN the credential scope for this change is evaluated
- THEN the platform MUST treat that capability as deferred
- AND the current change MUST proceed without adding service-account lifecycle behavior

#### Scenario: Inventory and generalized family management remain deferred

- GIVEN a requested capability requires credential inventory APIs, credential detail APIs, or
  generalized credential-family management
- WHEN the credential scope for this change is evaluated
- THEN the platform MUST treat that capability as deferred
- AND the current change MUST proceed without broadening beyond one API-key replacement capability

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

(Previously: Refresh credential lifecycle did not check email verification status)

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

#### Scenario: Refresh credential denied for unverified email

- GIVEN a refresh request presents a valid refresh credential
- AND the user's `email_status` is `UNVERIFIED`
- When the backend evaluates the refresh request
- THEN the system MUST deny the refresh request with 403 status
- AND the error MUST indicate email verification required
- AND the system MUST NOT issue a new access token

#### Scenario: Refresh credential accepted for verified email

- GIVEN a refresh request presents a valid refresh credential
- AND the user's `email_status` is `VERIFIED`
- When the backend evaluates the refresh request
- THEN the system MUST issue a new access token
- AND the refresh credential state MUST remain valid only according to current authoritative backend
  rules

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

(Previously: No email verification check in refresh flow)

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

### Requirement: Email Verification Gating for Refresh Credentials

The system MUST gate refresh credential issuance behind email verification status.

The system MUST check `email_status` before issuing new access tokens via refresh.
The system MUST reject refresh attempts for `UNVERIFIED` emails with 403 status.
The system MUST allow refresh attempts for `VERIFIED` emails.
The system MUST include email status check in refresh validation flow.

#### Scenario: Refresh gate blocks unverified email

- GIVEN a user attempts to refresh with valid refresh token
- AND the user's `email_status` is `UNVERIFIED`
- When the refresh handler processes the request
- THEN the system MUST check email verification status
- AND the system MUST reject with 403 status
- AND the error MUST indicate email verification required
- AND the system MUST NOT issue new JWT

#### Scenario: Refresh gate allows verified email

- GIVEN a user attempts to refresh with valid refresh token
- AND the user's `email_status` is `VERIFIED`
- When the refresh handler processes the request
- THEN the system MUST check email verification status
- AND the system MUST issue new JWT
- AND the system MUST return successful refresh response

#### Scenario: Refresh gate integrated with existing validation

- GIVEN the refresh handler already validates token validity
- When the email verification check is added
- THEN the system MUST perform email check after token validation
- AND the system MUST perform email check before token issuance
- AND the system MUST maintain existing refresh token validation logic

#### Scenario: Refresh cookie not set for unverified email

- GIVEN a user registers with valid credentials
- AND email verification is pending
- When the registration response is returned
- THEN the system MUST NOT set refresh cookie
- AND the system MUST NOT issue any tokens
- AND the response MUST indicate email verification required

