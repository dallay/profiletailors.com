# Delta for Registration

## ADDED Requirements

### Requirement: Registration Persists Atomically

The system MUST execute all persistent mutations for one registration attempt inside one atomic transaction.

The transaction MUST include identity creation, local password credential creation, default workspace provisioning, and email verification token creation. If any in-transaction mutation fails, the system MUST roll back all registration mutations from that attempt. Partial user, credential, workspace, membership, role, or verification-token records MUST NOT remain committed after rollback.

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

## MODIFIED Requirements

### Requirement: Registration Creates Authenticated Session

Registration SHALL create an authenticated session only after successful completion of the atomic registration transaction.

The registration handler SHALL issue JWT and refresh tokens only after the transaction commits successfully. The response SHALL be HTTP 201 Created with AuthTokens payload. The response SHALL set an HttpOnly refresh token cookie. The registration payload SHALL no longer return only RegistrationResult (breaking change from prior behavior). Post-registration side effects, including `UserRegistered` event publication, SHALL occur only after commit succeeds and SHALL NOT occur for rolled-back registrations.

(Previously: Registration issued tokens on successful handler completion without specifying transaction commit ordering or post-commit side-effect timing.)

#### Scenario: Registration creates session after commit

- GIVEN a new user submits valid registration payload with email and password
- WHEN the registration transaction commits successfully
- THEN the response SHALL be HTTP 201 Created
- AND the response SHALL include an access token and HttpOnly refresh token cookie
- AND the user SHALL be immediately authenticated

#### Scenario: Post-commit side effects run only after successful commit

- GIVEN a registration attempt reaches side-effect processing
- WHEN the registration transaction has committed successfully
- THEN the system SHALL publish `UserRegistered` and issue session credentials after commit
- AND if the transaction rolls back, the system SHALL NOT publish the event or issue session credentials
