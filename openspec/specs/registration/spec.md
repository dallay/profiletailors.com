# Delta for Registration

## Overview

Remove the `username` field from the registration form and API request payload. The backend
continues auto-deriving username from the email prefix. Username remains in API responses for future
profile settings.

## REMOVED Requirements

### Requirement: Username Field in Registration Form

The registration form MUST NOT include a username input field.

The frontend `AuthView.vue` MUST render only email and password fields for registration.
The `RegisterPayload` type in `auth-api.ts` MUST NOT include `username`.
The `registerWithPassword` method in `stores/auth.ts` MUST NOT accept `username`.
The i18n keys `auth.username` and `auth.usernamePlaceholder` MUST be removed.

#### Scenario: Registration form renders without username

- GIVEN a user navigates to the registration page
- WHEN the form renders
- THEN the user MUST see email and password inputs only
- AND the user MUST NOT see a username input

### Requirement: Username Field in Registration API Request

The registration API endpoint MUST NOT accept `username` in the request body.

The `RegisterUserRequest` DTO in `LocalAuthController.kt` MUST remove the `username` field.
Only `email` and `password` SHALL be accepted as request body parameters.

#### Scenario: Register request succeeds without username

- GIVEN a client sends a POST to `/api/auth/register`
- AND the body contains `{"email": "alice@example.com", "password": "Secret123!"}`
- WHEN the server processes the request
- THEN the response MUST be `201 Created`
- AND the user SHALL be created with username derived from the email prefix (`alice`)

#### Scenario: Register request with unknown username field is silently ignored

- GIVEN a client sends a POST to `/api/auth/register`
- AND the body includes `{"email": "bob@example.com", "password": "Secret123!", "username": "bob"}`
- WHEN the server processes the request
- THEN the server MUST silently ignore the unknown `username` field via Jackson default behavior
- AND the response MUST be `201 Created`
- AND the username SHALL be auto-derived from the email prefix (`bob`)

## PRESERVED Requirements

### Requirement: Backend Auto-Derives Username from Email

The system MUST continue to auto-derive the username from the email prefix when creating a user
account.

The `RegisterUserHandler` MUST derive the username by taking the email prefix (the part before `@`).
The derived username MUST be stored in the `username` column in the database.

#### Scenario: Username derived from simple email prefix

- GIVEN a user registers with email `alice@example.com`
- WHEN the account is created
- THEN the username MUST be set to `alice`
- AND the API response MUST include `username: "alice"`

#### Scenario: Username derived from email with special characters

- GIVEN a user registers with email `alice.smith+test@example.com`
- WHEN the account is created
- THEN the username MUST be derived from the full email prefix `alice.smith+test`

### Requirement: Registration Persists Atomically

The system MUST execute all persistent mutations for one registration attempt inside one atomic
transaction.

The transaction MUST include identity creation, local password credential creation, default
workspace provisioning, and email verification token creation. If any in-transaction mutation fails,
the system MUST roll back all registration mutations from that attempt. Partial user, credential,
workspace, membership, role, or verification-token records MUST NOT remain committed after rollback.

#### Scenario: Registration commits all records together

- GIVEN a new user submits a valid registration payload
- WHEN all registration mutations succeed inside one transaction
- THEN the system MUST commit the full registration state atomically
- AND the user, credential, workspace, membership, role, and verification token MUST all persist

#### Scenario: Registration failure rolls back all prior mutations

- GIVEN a new user submits a valid registration payload
- AND one registration mutation fails before commit
- WHEN the transaction completes with failure
- THEN the system MUST roll back all earlier registration mutations
- AND no partial registration records MUST remain persisted

### Requirement: Registration Creates Authenticated Session

Registration SHALL create an authenticated session only after successful completion of the atomic
registration transaction.

The registration handler SHALL issue JWT and refresh tokens only after the transaction commits
successfully. The response SHALL be HTTP 201 Created with AuthTokens payload. The response SHALL set
an HttpOnly refresh token cookie. The registration payload SHALL no longer return only
RegistrationResult (breaking change from prior behavior). Post-registration side effects, including
`UserRegistered` event publication, SHALL occur only after commit succeeds and SHALL NOT occur for
rolled-back registrations.

> **Historical note:** Prior to this change, registration issued tokens on successful handler
> completion without specifying transaction commit ordering or post-commit side-effect timing. The
> new
> contract commits first, then publishes and issues tokens.

#### Scenario: Registration creates session after commit

- GIVEN a new user submits valid registration payload with email and password
- WHEN the registration transaction commits successfully
- THEN the response SHALL be HTTP 201 Created
- AND the response SHALL include an access token and HttpOnly refresh token cookie
- AND the user SHALL be immediately authenticated

#### Scenario: Post-commit side effects run only after successful commit

- GIVEN a registration attempt with email `alice@example.com`
- WHEN the transaction commits successfully
- THEN the system SHALL publish `UserRegistered` event for `alice@example.com`
- AND the system SHALL issue a refresh session for the new principal
- AND the response SHALL include an access token
-
- GIVEN a registration attempt with email `bob@example.com`
- AND a workspace provisioning failure occurs inside the transaction
- WHEN the transaction rolls back
- THEN the system SHALL NOT publish any `UserRegistered` event
- AND the system SHALL NOT issue any refresh session
- AND no user_identities, credentials, workspaces, or verification tokens SHALL persist for
  `bob@example.com`

### Requirement: Username in API Responses

The system MUST continue to include `username` in API responses.

The `AuthTokens` and `CurrentUserProfile` response DTOs MUST retain the `username` field.
Removing `username` from the response is out of scope for this change.

#### Scenario: Login response includes username

- GIVEN a registered user with a derived username
- WHEN the user logs in
- THEN the response MUST include `username` in the token payload

#### Scenario: Profile response includes username

- GIVEN an authenticated user
- WHEN they request their current profile
- THEN the response MUST include the `username` field

## ADDED Requirements (DALLAY-509)

### Requirement: Registration Availability Configuration

The backend MUST bind registration availability from typed, non-secret configuration, MUST default
it to `false`, and MUST require an explicit override to enable registration. Operator documentation
MUST describe the setting without secrets.

#### Scenario: Missing configuration fails closed

- GIVEN no registration availability override is configured
- WHEN the application starts
- THEN registration MUST be disabled

#### Scenario: Explicit override enables registration

- GIVEN the registration availability override is `true`
- WHEN the application starts
- THEN registration MUST be enabled

### Requirement: Backend-Authoritative Registration Gate

When registration is disabled, `POST /api/auth/register` MUST be rejected before command dispatch or mutation with exact HTTP 503 Problem Details and `code: "REGISTRATION_DISABLED"`. No command, persistence, event, or session mutation MUST occur. When enabled, existing registration behavior and atomicity MUST remain unchanged.

#### Scenario: Direct registration is denied without side effects

- GIVEN registration is disabled
- WHEN a client posts valid registration data directly
- THEN the response MUST be exact HTTP 503 with `code: "REGISTRATION_DISABLED"`
- AND no command, persistence, event, or session mutation MUST occur

#### Scenario: Enabled registration remains functional

- GIVEN registration is enabled
- WHEN a new user submits valid registration data
- THEN the response MUST be `201 Created`
- AND the existing atomic registration and authenticated-session behavior MUST occur

### Requirement: Registration UI Fails Closed

The SPA MUST show registration entry points only after the public capability resolves enabled. Capability-read failure or malformed data MUST close registration UI and direct access, MUST NOT be treated as security enforcement, and MUST NOT block login. Direct `/register` access while unavailable MUST preserve the requested route, render “Registration is currently unavailable” with named-route navigation to login, omit the form, and MUST NOT call registration.

#### Scenario: Registration UI follows enabled capability

- GIVEN the capability resolves with registration enabled
- WHEN a guest views login or requests `/register`
- THEN the named registration entry and registration form MUST be available

#### Scenario: Capability failure closes registration only

- GIVEN the capability request fails or returns a malformed value
- WHEN a guest opens login or `/register`
- THEN registration MUST be unavailable without redirecting `/register`
- AND login MUST remain usable

#### Scenario: Disabled route suppresses registration

- GIVEN registration resolves disabled
- WHEN a guest directly requests `/register`
- THEN the unavailable state and named login navigation MUST render instead of the form
- AND no registration request MUST be sent

## ADDED Requirements (DALLAY-561)

### Requirement: Mutually Exclusive Registration Policy

The backend MUST represent registration availability as one of `OPEN`, `INVITE_ONLY`, or `CLOSED`.
The policy MUST be evaluated server-side before a public registration command can perform any
normalization, persistence, event publication, or session issuance.

#### Scenario: Open mode permits public registration

- GIVEN registration mode is `OPEN`
- WHEN a visitor submits valid registration details
- THEN the existing registration flow MUST proceed

#### Scenario: Invite-only mode rejects direct public registration

- GIVEN registration mode is `INVITE_ONLY`
- WHEN a visitor submits valid registration details without a validated invitation context
- THEN registration MUST be rejected with a safe invitation-required error
- AND no registration mutation or side effect MUST occur

#### Scenario: Closed mode rejects public registration

- GIVEN registration mode is `CLOSED`
- WHEN a visitor submits valid registration details
- THEN registration MUST be rejected as unavailable
- AND no registration mutation or side effect MUST occur

### Requirement: Typed Registration Mode Configuration

The backend MUST bind `app.identity.registration.mode` from the non-secret
`SMP_REGISTRATION_MODE` environment variable. Missing configuration MUST default to `CLOSED`.

#### Scenario: Missing mode fails closed

- GIVEN `SMP_REGISTRATION_MODE` is not configured
- WHEN the application binds identity configuration
- THEN the registration mode MUST be `CLOSED`

#### Scenario: Explicit mode binds

- GIVEN `SMP_REGISTRATION_MODE` is `INVITE_ONLY`
- WHEN the application binds identity configuration
- THEN the registration mode MUST be `INVITE_ONLY`

### Requirement: Public Capability Reflects Public Registration

The public capability response MUST report `registrationEnabled: true` only for `OPEN` mode. It
MUST report `false` for `INVITE_ONLY` and `CLOSED` without exposing additional operational state.

## Acceptance Criteria

| ID   | Criterion                                        | Validation                        |
|------|--------------------------------------------------|-----------------------------------|
| AC-1 | Registration form has no username field          | Visual inspection + DOM query     |
| AC-2 | API register succeeds without `username` in body | Integration test                  |
| AC-3 | Username auto-derived from email prefix          | Unit test coverage                |
| AC-4 | Existing tests pass                              | `./gradlew test` green            |
| AC-5 | No dead i18n strings for registration username   | Grep confirms removal             |
| AC-6 | API responses still include `username`           | Integration test asserts response |

## Constraints

- No DB migration — `username` column stays, data stays
- Username removal is registration-only; profile settings edit is deferred
- All existing tests MUST pass with zero regressions
