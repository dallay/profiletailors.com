# Delta for Credentials

## MODIFIED Requirements

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
- When logout completes through the dedicated logout flow
- THEN the backend MUST clear the refresh cookie from the browser-facing response
- AND a later refresh attempt with the cleared session state MUST be denied unless a new login
  occurs

#### Scenario: Refresh cookie not set for unverified email

- GIVEN a user registers with valid credentials
- AND email verification is pending
- When the registration response is returned
- THEN the system MUST NOT set refresh cookie
- AND the system MUST NOT issue any tokens
- AND the response MUST indicate email verification required

## ADDED Requirements

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