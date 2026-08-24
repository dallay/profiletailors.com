# Delta for Auth Security Hardening

## Purpose

Allow users with unverified emails (PENDING) to authenticate and create sessions while enforcing a
feature-level email verification policy for protected operations.

## ADDED Requirements

### Requirement: Authenticated Session for Unverified Users

The system SHALL allow users with `emailStatus = PENDING` to authenticate and receive a valid
session.

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

### Requirement: Immediate Session Creation After Registration

The system SHALL create an authenticated session immediately upon successful registration.

Registration SHALL return an access token and set an HttpOnly refresh token cookie in the response.
The response SHALL be HTTP 201 Created with the AuthTokens payload.
Registration payload SHALL no longer return only RegistrationResult (breaking change from prior
behavior).

#### Scenario: Registration creates session and returns tokens

- GIVEN a new user submits valid registration payload with email and password
- WHEN the registration handler processes the request successfully
- THEN the response SHALL be HTTP 201 Created
- AND the response SHALL include an access token in the body
- AND the response SHALL set an HttpOnly refresh token cookie
- AND the user SHALL be immediately authenticated

#### Scenario: Registration response matches AuthTokens payload

- GIVEN a user completes registration
- WHEN the response is returned
- THEN the payload SHALL conform to the AuthTokens schema
- AND SHALL NOT conform to the legacy RegistrationResult schema

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
- AND the problem detail SHALL include
  `type: "https://api.profiletailors.com/errors/email-verification-required"`

## MODIFIED Requirements

### Requirement: JWT-First Identity Materialization for Phase One

(Previously: Login guard rejects unverified email; Refresh guard rejects unverified email)

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
The system MUST treat credential transport as an authentication and principal materialization seam,
not as the source of authorization truth.
The system MUST NOT rely on credential claims or credential presence alone to determine workspace
membership, permission grants, or effective authorization.

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

## Summary Table

| Domain       | Type  | Requirements                   | Scenarios |
|--------------|-------|--------------------------------|-----------|
| identity     | Delta | 2 added, 2 modified, 2 removed | 6         |
| registration | Delta | 1 modified                     | 2         |

## Coverage

- Happy paths: Login for PENDING users, Refresh for PENDING users, Registration creates session ✓
- Edge cases: Email status in JWT claims, Breaking change in registration response ✓
- Error states: EMAIL_VERIFICATION_REQUIRED problem detail structure ✓
