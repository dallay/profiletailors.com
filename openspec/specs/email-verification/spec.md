# Email Verification Specification

## Purpose

Define the email verification lifecycle for user registration. This specification establishes token
generation, email dispatch, token consumption, and email status tracking to ensure users verify
their email addresses before accessing protected platform capabilities.

## Requirements

### Requirement: Email Verification Token Lifecycle

The system MUST manage email verification tokens with secure generation, storage, and consumption.

The system MUST generate cryptographically secure tokens (minimum 32 bytes entropy).
The system MUST store tokens as SHA-256 hashes in the database.
The system MUST enforce single-use consumption (token invalidated after successful verification).
The system MUST enforce 24-hour expiration for tokens.
The system MUST associate tokens with specific user emails.
The system MUST provide cleanup for expired tokens (background job or migration).
(Previously: users consumed verification tokens through a backend GET link and successful verification issued session tokens directly.)

#### Scenario: Token generated on user registration

- GIVEN a new user submits registration with valid email and password
- WHEN the registration handler completes successfully
- THEN the system MUST generate a verification token
- AND the token MUST be stored hashed in `email_verification_tokens` table
- AND the token MUST be linked to the user's email address
- AND the token MUST have a 24-hour expiration timestamp

#### Scenario: Token validated on verification endpoint

- GIVEN a user opens the verification landing route with a token
- WHEN the client calls `POST /api/auth/verify-email` with `{ token }`
- THEN the system MUST hash the presented token
- AND the system MUST verify the token is not expired and not used

#### Scenario: Token consumed after successful verification

- GIVEN a valid token is submitted for verification
- WHEN the verification completes successfully
- THEN the system MUST mark the token as used
- AND the system MUST set `email_status = VERIFIED` for the associated user
- AND the system MUST NOT require backend GET-link consumption

#### Scenario: Expired token rejected

- GIVEN a token older than 24 hours is presented
- WHEN the system validates the token
- THEN the system MUST reject with 400 status
- AND the error MUST indicate token expiration
- AND the system MUST NOT update email status

#### Scenario: Invalid token rejected

- GIVEN a token that does not exist in the database is presented
- WHEN the system validates the token
- THEN the system MUST reject with 400 status
- AND the error MUST indicate invalid token
- AND the system MUST NOT update email status

#### Scenario: Used token rejected

- GIVEN a token that has already been consumed is presented
- WHEN the system validates the token
- THEN the system MUST reject with 400 status
- AND the error MUST indicate token already used
- AND the system MUST NOT update email status

### Requirement: Email Verification API Endpoints

The system MUST provide HTTP endpoints for email verification and resend functionality.

The system MUST provide `POST /api/auth/verify-email` endpoint for token-based verification.
The system MUST provide `POST /api/auth/resend-verification` endpoint for resending verification
emails.
The verify endpoint MUST accept token in the JSON request body as `{ token }`.
The resend endpoint MUST accept email in request body.
Both endpoints MUST follow RESTful response conventions.
(Previously: published docs described verification as `GET /api/auth/verify-email` with a token query parameter; the implemented contract is SPA link entry plus backend POST.)

#### Scenario: Verify endpoint returns success on valid token

- GIVEN a user submits `POST /api/auth/verify-email` with a valid token
- WHEN the token is valid and not expired
- THEN the system MUST return 200 OK
- AND the response MUST reflect successful verification

#### Scenario: Verify endpoint returns error on invalid token

- GIVEN a user submits `POST /api/auth/verify-email` with an invalid or expired token
- WHEN verification fails
- THEN the system MUST return 400 Bad Request
- AND the response MUST include token-validity error details

#### Scenario: Resend endpoint returns accepted

- GIVEN a user calls `POST /api/auth/resend-verification` with valid email
- WHEN the request is processed
- THEN the system MUST return 202 Accepted
- AND the system MUST generate new verification token
- AND the system MUST invalidate previous tokens for that email
- AND the system MUST dispatch verification email asynchronously

#### Scenario: Resend endpoint prevents email enumeration

- GIVEN a user calls resend endpoint with non-existent email
- WHEN the request is processed
- THEN the system MUST return 202 Accepted (same as valid email)
- AND the system MUST NOT send any email
- AND the system MUST NOT reveal whether email exists

#### Scenario: Resend endpoint validates email format

- GIVEN a user calls resend endpoint with invalid email format
- WHEN the request is validated
- THEN the system MUST return 400 Bad Request
- AND the error MUST indicate invalid email format

### Requirement: Email Verification Consumes Token and Updates Status Atomically

The system MUST consume a verification token and update the associated email status within one atomic persistence boundary. If either persistence mutation fails, both mutations SHALL roll back. Successful verification MAY issue auth/session material only after the atomic persistence boundary succeeds.

#### Scenario: Verification commits token use and verified status

- GIVEN a valid unused verification token for an unverified email
- WHEN email verification completes successfully
- THEN the token MUST be marked used
- AND the associated email status MUST become `VERIFIED`
- AND session issuance MAY occur after successful persistence

#### Scenario: Failed status update does not consume token

- GIVEN a valid unused verification token
- AND updating the associated email status fails before transaction commit
- WHEN the verification handler returns an error
- THEN the token MUST remain unused
- AND the email status MUST remain unchanged
- AND no success/session response MUST be issued

### Requirement: Resend Verification Replaces Tokens Atomically

The system MUST invalidate prior verification tokens and create the replacement token within one atomic persistence boundary. Verification email events SHALL be published only after successful transaction commit.

#### Scenario: Resend commits invalidation and new token

- GIVEN an unverified user has existing unused verification tokens
- WHEN resend verification is accepted
- THEN prior unused tokens MUST be invalidated
- AND exactly one new active verification token MUST be persisted
- AND verification email dispatch MAY be published after commit

#### Scenario: New-token creation failure preserves old tokens

- GIVEN an unverified user has existing unused verification tokens
- AND replacement token creation fails before transaction commit
- WHEN the resend handler returns an error
- THEN prior unused tokens MUST remain valid according to their original state
- AND no replacement token MUST be persisted
- AND no verification email event MUST be published

### Requirement: Verification Email Dispatch Reliability

The system MUST reliably dispatch verification emails for both registration and resend flows.

Registration and resend flows MUST trigger the same verification-email delivery path, and a successful API response MUST leave the account in a state where a verification email can be delivered without additional manual operator action.

#### Scenario: Registration triggers deliverable verification email

- GIVEN a new user completes registration successfully
- WHEN registration returns success
- THEN the system MUST create a valid verification token
- AND the system MUST trigger verification email dispatch for that token

#### Scenario: Resend replaces the active verification email

- GIVEN an existing unverified user requests resend
- WHEN the resend request is accepted
- THEN the system MUST invalidate prior unused tokens
- AND the system MUST trigger dispatch for the newly issued token

### Requirement: Current User Profile Exposes Authoritative Email Status

The system MUST expose the current user's authoritative email verification status through `GET /api/auth/me`.

The profile response MUST include `emailStatus`, and clients MUST treat that field as the source of truth for verification-dependent UX and capability gating.

#### Scenario: Unverified profile returns authoritative status

- GIVEN an authenticated user whose email is not verified
- WHEN the client requests `GET /api/auth/me`
- THEN the response MUST include `emailStatus = UNVERIFIED`
- AND the response MUST remain successful if the session itself is otherwise valid

#### Scenario: Verified profile returns authoritative status

- GIVEN an authenticated user whose email is verified
- WHEN the client requests `GET /api/auth/me`
- THEN the response MUST include `emailStatus = VERIFIED`
- AND clients MUST be able to use that value without token heuristics

### Requirement: Email Verification Token Invalidation

The system MUST invalidate verification tokens when they are no longer needed.

The system MUST invalidate all tokens for an email when a new token is generated (resend).
The system MUST invalidate the specific token after successful verification.
The system MUST invalidate tokens when user email is changed (future use).
The system MUST support bulk cleanup of expired tokens.

#### Scenario: Resend invalidates previous tokens

- GIVEN a user requests resend verification
- WHEN the new token is generated
- THEN the system MUST delete all previous unused tokens for that email
- AND the new token MUST be the only valid token for that email

#### Scenario: Verification invalidates used token

- GIVEN a user verifies email with valid token
- WHEN verification completes
- THEN the system MUST mark the token as used
- AND the system MUST NOT allow the same token to be used again

#### Scenario: Expired tokens cleaned up

- GIVEN expired tokens exist in the database
- WHEN a cleanup job runs (or migration)
- THEN the system MUST remove tokens older than 24 hours
- AND the system MUST log cleanup activity

### Requirement: Email Verification Error Handling

The system MUST handle email verification errors gracefully.

The system MUST return appropriate HTTP status codes for different error conditions.
The system MUST provide meaningful error messages without exposing security details.
The system MUST log verification failures for monitoring.
The system MUST not leak information about email existence.

#### Scenario: Verification failure logged

- GIVEN a verification attempt fails (invalid token, expired, etc.)
- WHEN the failure is processed
- THEN the system MUST log the failure with timestamp
- AND the system MUST NOT log the actual token value
- AND the system MUST include error reason in logs

#### Scenario: Rate limiting considered for resend

- GIVEN a user requests resend verification multiple times
- WHEN the requests are processed
- THEN the system SHOULD apply rate limiting (future implementation)
- AND the system MUST currently process all requests (no rate limit yet)

#### Scenario: Concurrent verification attempts handled

- GIVEN multiple verification attempts with same token
- WHEN the first attempt succeeds
- THEN subsequent attempts MUST fail with appropriate error
- AND the system MUST maintain data consistency

### Requirement: Email Verification Database Schema

The system MUST persist email verification data in the database.

The system MUST add `email_status` column to `user_identities` table.
The system MUST create `email_verification_tokens` table.
The system MUST support foreign key relationships to user identities.
The system MUST include proper indexes for performance.

#### Scenario: Email status column added

- GIVEN the database migration runs
- WHEN the migration completes
- THEN `user_identities` table MUST have `email_status` column
- AND the column MUST be NOT NULL with default 'UNVERIFIED'
- AND existing rows MUST be updated to 'VERIFIED'

#### Scenario: Verification tokens table created

- GIVEN the database migration runs
- WHEN the migration completes
- THEN `email_verification_tokens` table MUST exist
- AND the table MUST have columns: id, email, token_hash, expires_at, used_at, created_at
- AND there MUST be an index on email column
- AND there MUST be an index on token_hash column

#### Scenario: Foreign key constraints enforced

- GIVEN a verification token is created
- WHEN the token is persisted
- THEN the token MUST be linked to a valid user email
- AND deleting a user SHOULD cascade delete their tokens

### Requirement: Verification Flow Contract Alignment

The system MUST keep user-visible verification behavior and published contracts aligned across specs and product documentation.

#### Scenario: Verification flow is described consistently

- GIVEN a user or operator reads platform docs or specs
- WHEN the verification flow is described
- THEN it MUST reference the frontend verification route and backend POST contract
- AND it MUST NOT describe verification as a backend GET-link flow
