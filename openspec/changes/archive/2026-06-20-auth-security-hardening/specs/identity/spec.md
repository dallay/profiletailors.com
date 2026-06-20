# Delta for Identity — Auth Security Hardening

## ADDED Requirements

### Requirement: Authenticated Session for Unverified Users

The system SHALL allow users with `emailStatus = PENDING` to authenticate and receive a valid session.

Login SHALL return a valid session comprising an access token and a refresh token cookie.
The access token SHALL include `emailStatus = PENDING` in the JWT claims.
Refresh SHALL succeed for users with `emailStatus = PENDING`.
No feature gating SHALL be applied within the authentication flow itself.

#### Scenario: Login succeeds for unverified user

- GIVEN a user exists with `emailStatus = PENDING`
- WHEN the user submits valid credentials to the login endpoint
- THEN the system SHALL return HTTP 200 with AuthTokens payload
- AND the response SHALL include an access token with `emailStatus = PENDING` in claims
- AND the response SHALL set an HttpOnly refresh token cookie

#### Scenario: Refresh succeeds for unverified user

- GIVEN a user has a valid refresh token cookie
- AND the user's `emailStatus = PENDING`
- WHEN the user calls the refresh endpoint
- THEN the system SHALL return a new access token
- AND the access token SHALL include `emailStatus = PENDING` in claims

#### Scenario: Login includes email status in claims

- GIVEN a user with `emailStatus = PENDING` authenticates successfully
- WHEN the system generates the access token
- THEN the JWT claims SHALL include `emailStatus: "PENDING"`

### Requirement: EMAIL_VERIFICATION_REQUIRED Error Code

When `UnverifiedEmailException` is thrown, the system SHALL return a structured problem detail.

The problem detail SHALL include:
- `status`: 403
- `title`: "Email verification required"
- `detail`: "Please verify your email before using this feature."
- `code`: "EMAIL_VERIFICATION_REQUIRED"
- `type`: "https://api.profiletailors.com/errors/email-verification-required"

#### Scenario: Feature-gated endpoint returns structured error

- GIVEN a user with `emailStatus = PENDING` attempts to access a feature that requires verification
- WHEN the system throws `UnverifiedEmailException`
- THEN the response SHALL be HTTP 403
- AND the response body SHALL be a valid RFC 9457 problem detail
- AND the problem detail SHALL include `code: "EMAIL_VERIFICATION_REQUIRED"`
- AND the problem detail SHALL include `type: "https://api.profiletailors.com/errors/email-verification-required"`

### Requirement: EmailVerificationPolicy Interface (Design Only)

The system SHOULD define an `EmailVerificationPolicy` interface in `identity/application`.

The interface SHALL declare: `requiresVerification(feature: AuthFeature): Boolean`
The interface SHALL define an enum `AuthFeature` with values: `PUBLISH_CONTENT`, `INVITE_TEAM`, `CONNECT_SOCIAL`, `ACCESS_BILLING`, and future extensibility.
The default implementation SHALL return `true` for all features (all features require VERIFIED status).

This requirement is DESIGN ONLY. Implementation is deferred.

#### Scenario: EmailVerificationPolicy interface design

- GIVEN the design specifies EmailVerificationPolicy in identity/application
- WHEN the design is reviewed
- THEN the interface SHALL declare `requiresVerification(feature: AuthFeature): Boolean`
- AND the AuthFeature enum SHALL include PUBLISH_CONTENT, INVITE_TEAM, CONNECT_SOCIAL, ACCESS_BILLING
- AND a default implementation SHALL specify all features require verification

## MODIFIED Requirements

### Requirement: JWT-First Identity Materialization for Phase One

(Previously: Login guard rejects unverified email; Refresh guard rejects unverified email)

The system MUST support repo-local authenticated principal materialization for the proving slice.

The system MUST derive the authenticated principal identity from a validated credential through repo-local identity seams.
For USER principals on the proving slice, the system MUST continue to materialize the authenticated principal from a validated JWT.
For the local USER browser session flow, the frontend MUST obtain that JWT access token through login or refresh and keep it only in memory for subsequent protected API calls.
For the dedicated refresh endpoint, the backend MUST materialize the same USER principal only after validating the refresh credential against authoritative backend state and issuing a new JWT for the session.
The system MUST treat credential transport as an authentication and principal materialization seam, not as the source of authorization truth.
The system MUST NOT rely on credential claims or credential presence alone to determine workspace membership, permission grants, or effective authorization.

#### Scenario: Login succeeds for unverified user (MODIFIED)

- GIVEN a user exists with `emailStatus = PENDING`
- WHEN the user submits valid credentials to the login endpoint
- THEN the system SHALL return HTTP 200 with AuthTokens payload
- AND the response SHALL include an access token with `emailStatus = PENDING` in claims
- AND the response SHALL set an HttpOnly refresh token cookie

#### Scenario: Refresh succeeds for unverified user (MODIFIED)

- GIVEN a user has a valid refresh token cookie
- AND the user's `emailStatus = PENDING`
- WHEN the user calls the refresh endpoint
- THEN the system SHALL return a new access token
- AND the access token SHALL include `emailStatus = PENDING` in claims

#### Scenario: Registration emits domain event and creates session (MODIFIED)

- GIVEN a new user submits registration with valid email and password
- WHEN the registration handler processes the request
- THEN the system MUST persist the user with `email_status = PENDING`
- AND the system MUST emit a `UserRegistered` domain event
- AND the system SHALL issue JWT and refresh tokens
- AND the response SHALL be HTTP 201 with AuthTokens payload

## REMOVED Requirements

### Requirement: Login Guard Rejects Unverified Email

(Reason: Replaced by authenticated session for unverified users)

The system MUST reject login attempts for users with `email_status = UNVERIFIED`.

(Previously: Users with unverified email could not log in)

### Requirement: Refresh Guard Rejects Unverified Email

(Reason: Replaced by authenticated session for unverified users)

The system MUST reject refresh attempts for users with `email_status = UNVERIFIED`.

(Previously: Users with unverified email could not refresh their session)
