# Delta for Identity

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

(Previously: Registration issued JWT+refresh immediately without email verification)

#### Scenario: Valid JWT materializes an authenticated principal

- GIVEN a protected phase-one request includes a valid JWT
- WHEN the platform authenticates the request
- THEN the system MUST materialize an authenticated principal through repo-local identity seams
- AND downstream application behavior MUST consume the platform principal rather than raw token
  structures

#### Scenario: Invalid JWT blocks protected identity establishment

- GIVEN a protected request includes a missing or invalid JWT
- WHEN the platform evaluates authentication
- THEN the system MUST reject the request as unauthenticated
- AND no protected use case MUST execute

#### Scenario: Valid service-account bearer credential materializes an authenticated principal

- GIVEN a protected request to `/api/authorization/workspace-access/current` includes a valid
  service-account bearer credential
- WHEN the platform authenticates the request
- THEN the system MUST materialize an authenticated principal with principal type `SERVICE_ACCOUNT`
- AND downstream application behavior MUST consume the platform principal rather than raw credential
  structures

#### Scenario: Missing or invalid service-account credential blocks principal establishment

- GIVEN a protected request to `/api/authorization/workspace-access/current` includes a missing,
  invalid, or non-materializable service-account bearer credential
- WHEN the platform evaluates authentication
- THEN the system MUST reject the request as unauthenticated
- AND no protected use case MUST execute

#### Scenario: Valid API key materializes an authenticated principal

- GIVEN a protected request to `/api/authorization/workspace-access/current` includes a valid API
  key bound to a persisted principal
- WHEN the platform authenticates the request
- THEN the system MUST materialize an authenticated principal with principal type `API_KEY`
- AND downstream application behavior MUST consume the platform principal rather than raw credential
  structures

#### Scenario: Missing, invalid, or non-materializable API key blocks principal establishment

- GIVEN a protected request to `/api/authorization/workspace-access/current` includes a missing,
  invalid, inactive, revoked, or non-materializable API key
- WHEN the platform evaluates authentication
- THEN the system MUST reject the request as unauthenticated
- AND no protected use case MUST execute

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

#### Scenario: Registration emits domain event without issuing tokens

- GIVEN a new user submits registration with valid email and password
- WHEN the registration handler processes the request
- THEN the system MUST persist the user with `email_status = UNVERIFIED`
- AND the system MUST emit a `UserRegistered` domain event
- AND the system MUST NOT issue JWT or refresh tokens
- AND the response MUST return 201 with verification instructions

#### Scenario: Login guard rejects unverified email

- GIVEN a user attempts to login with valid credentials
- AND the user's `email_status` is `UNVERIFIED`
- WHEN the login handler processes the request
- THEN the system MUST reject the request with 403 status
- AND the error MUST indicate email verification required
- AND the system MUST NOT issue JWT or refresh tokens

#### Scenario: Login guard accepts verified email

- GIVEN a user attempts to login with valid credentials
- AND the user's `email_status` is `VERIFIED`
- WHEN the login handler processes the request
- THEN the system MUST issue JWT and refresh tokens
- AND the system MUST return successful authentication response

#### Scenario: Refresh guard rejects unverified email

- GIVEN a user attempts to refresh with valid refresh token
- AND the user's `email_status` is `UNVERIFIED`
- WHEN the refresh handler processes the request
- THEN the system MUST reject the request with 403 status
- AND the error MUST indicate email verification required
- AND the system MUST NOT issue new JWT

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

(Previously: No email verification check during session bootstrap)

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

#### Scenario: Refresh bootstrap fails for unverified email

- GIVEN a browser starts the app without an in-memory access token
- AND a valid refresh cookie is available but user email is unverified
- WHEN the frontend performs session bootstrap
- THEN the system MUST reject the refresh with 403 status
- AND the frontend MUST treat the browser as unauthenticated

## ADDED Requirements

### Requirement: Email Verification Token Management

The system MUST support email verification tokens for confirming user email addresses.

The system MUST generate a cryptographically secure token for each new user registration.
The token MUST be single-use and expire after 24 hours.
The token MUST be stored hashed (SHA-256) in the database.
The system MUST provide an endpoint to verify email using the token.
The system MUST provide an endpoint to resend verification email.

#### Scenario: Verification token generated on registration

- GIVEN a new user registers with valid email and password
- WHEN the registration completes
- THEN the system MUST generate a verification token
- AND the token MUST be stored hashed in `email_verification_tokens` table
- AND the token MUST be associated with the user's email

#### Scenario: Verify email endpoint validates token

- GIVEN a user receives verification email with token
- WHEN the user calls `GET /api/auth/verify-email?token=...`
- THEN the system MUST validate the token exists and is not expired
- AND the system MUST set `email_status = VERIFIED` for the user
- AND the system MUST invalidate the token (single-use)
- AND the system MUST issue JWT and refresh tokens
- AND the system MUST return successful verification response

#### Scenario: Verify email endpoint rejects invalid token

- GIVEN a user calls `GET /api/auth/verify-email?token=invalid`
- WHEN the system validates the token
- THEN the system MUST reject with 400 status
- AND the error MUST indicate invalid or expired token

#### Scenario: Verify email endpoint rejects expired token

- GIVEN a user calls verification endpoint with token older than 24 hours
- WHEN the system validates the token
- THEN the system MUST reject with 400 status
- AND the error MUST indicate token expired

#### Scenario: Resend verification sends new token

- GIVEN a user calls `POST /api/auth/resend-verification` with valid email
- WHEN the system processes the request
- THEN the system MUST generate a new verification token
- AND the system MUST invalidate any previous tokens for that email
- AND the system MUST send verification email with new token
- AND the system MUST return 202 Accepted response

#### Scenario: Resend verification rejects invalid email

- GIVEN a user calls resend endpoint with non-existent email
- WHEN the system processes the request
- THEN the system MUST return 202 Accepted (to prevent email enumeration)
- AND the system MUST NOT send any email

### Requirement: Email Verification Status Tracking

The system MUST track email verification status for all users.

The system MUST add `email_status` column to `user_identities` table.
The system MUST support two statuses: `UNVERIFIED` and `VERIFIED`.
New registrations MUST default to `UNVERIFIED`.
Existing users MUST be migrated to `VERIFIED` during schema migration.
The system MUST include email status in authentication responses.

#### Scenario: New registration has unverified status

- GIVEN a new user registers
- WHEN the user record is persisted
- THEN `email_status` MUST be `UNVERIFIED`

#### Scenario: Existing users migrated to verified status

- GIVEN the database migration runs for existing users
- WHEN the migration completes
- THEN all existing user records MUST have `email_status = VERIFIED`

#### Scenario: Email status included in auth response

- GIVEN a user successfully authenticates
- WHEN the authentication response is returned
- THEN the response MUST include `email_status` field
- AND the frontend MUST be able to read verification status