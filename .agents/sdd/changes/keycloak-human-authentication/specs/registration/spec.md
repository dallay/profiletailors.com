# Delta for Registration

## ADDED Requirements

### Requirement: Post-Cutover Signup Uses Keycloak

After the approved human-authentication cutover is complete, human signup and credential creation MUST
use Keycloak and MUST remain invite-only. Profile Tailors MUST NOT accept local human credentials to
create or authenticate an account. Keycloak owns human signup, credential creation, email
verification, and authentication. Profile Tailors continues to own local principal and product/account
state, workspace provisioning and authorization, platform permissions, and audit. Local principal
provisioning MUST remain manual. Successful Keycloak signup MUST resolve only to an already manually
provisioned local principal; matching email addresses alone MUST NOT merge or link accounts. Local
registration policy and capability reporting MUST NOT be interpreted as permission to create local
human credentials or permit public signup after cutover. The local password-based registration,
credential creation, email-verification, and
authenticated-session behavior in the current registration specification applies only to
pre-cutover operation and MUST NOT override this requirement. This requirement does not claim that
cutover has been implemented.

#### Scenario: Invite-only signup uses Keycloak after cutover

- GIVEN the approved human-authentication cutover is complete
- AND the human has a valid invitation
- WHEN the human follows the supported signup entry
- THEN the guest MUST enter the Keycloak-owned signup and credential-creation flow
- AND Profile Tailors MUST NOT submit a local password registration request

#### Scenario: Public signup remains unavailable after cutover

- GIVEN the approved human-authentication cutover is complete
- WHEN an uninvited guest attempts signup
- THEN the guest MUST NOT create a human account
- AND Profile Tailors MUST NOT provision a local principal automatically

#### Scenario: Signup does not provision a local principal automatically

- GIVEN a human completes Keycloak signup
- AND no local principal was provisioned manually
- WHEN the BFF resolves the authenticated identity
- THEN authentication MUST fail closed
- AND no local principal MUST be created from signup or email equality

#### Scenario: Local registration endpoint receives credentials after cutover

- GIVEN the approved human-authentication cutover is complete
- WHEN a client submits local email/password registration data
- THEN Profile Tailors MUST NOT create local human credentials or authenticate the account
- AND no local registration side effect MUST occur

#### Scenario: Registration capability does not authorize public or local signup after cutover

- GIVEN the approved human-authentication cutover is complete
- AND local registration policy or capability reporting indicates that registration is enabled
- WHEN a guest selects registration
- THEN the capability MUST NOT authorize public signup or local human credential creation
- AND only an invited human MAY enter the Keycloak-owned flow

## MODIFIED Requirements

### Requirement: Registration Persists Atomically

Before the approved human-authentication cutover, the system MUST execute all persistent mutations
for one local registration attempt inside one atomic transaction. The transaction MUST include
identity creation, local password credential creation, default workspace provisioning, and email
verification token creation. If any in-transaction mutation fails, the system MUST roll back all
registration mutations from that attempt. Partial user, credential, workspace, membership, role, or
verification-token records MUST NOT remain committed after rollback. After the approved cutover,
local signup MUST use the Keycloak-owned lifecycle under the approved principal-linking and
provisioning policy, and the local registration transaction MUST NOT create human password
credentials or email-verification tokens. (Previously: local registration transactions included
password credential and email-verification token creation without a cutover boundary.)

#### Scenario: Registration commits all records together before cutover

- GIVEN a new user submits a valid local registration payload before the approved cutover
- WHEN all registration mutations succeed inside one transaction
- THEN the system MUST commit the full registration state atomically
- AND the user, credential, workspace, membership, role, and verification token MUST all persist

#### Scenario: Registration failure rolls back all prior mutations before cutover

- GIVEN a new user submits a valid local registration payload before the approved cutover
- AND one registration mutation fails before commit
- WHEN the transaction completes with failure
- THEN the system MUST roll back all earlier registration mutations
- AND no partial registration records MUST remain persisted

### Requirement: Registration Creates Authenticated Session

Before the approved human-authentication cutover, registration SHALL create an authenticated session
only after successful completion of the atomic registration transaction. The registration handler
SHALL issue JWT and refresh tokens only after the transaction commits successfully. The response
SHALL be HTTP 201 Created with AuthTokens payload and SHALL set an HttpOnly refresh token cookie. The
registration payload SHALL no longer return only RegistrationResult (breaking change from prior
behavior). Post-registration side effects, including `UserRegistered` event publication, SHALL occur
only after commit succeeds and SHALL NOT occur for rolled-back registrations. After the approved
cutover, local registration MUST NOT issue local access or refresh credentials; the human signup and
authentication lifecycle MUST use Keycloak and the approved relying-party session policy. (Previously:
local registration issued local access and refresh credentials after commit without a cutover
boundary.)

> **Historical note:** Prior to this change, registration issued tokens on successful handler
> completion without specifying transaction commit ordering or post-commit side-effect timing. The
> new contract commits first, then publishes and issues tokens.

#### Scenario: Registration creates session after commit before cutover

- GIVEN a new user submits valid local registration payload with email and password before the approved cutover
- WHEN the registration transaction commits successfully
- THEN the response SHALL be HTTP 201 Created
- AND the response SHALL include an access token and HttpOnly refresh token cookie
- AND the user SHALL be immediately authenticated

#### Scenario: Post-commit side effects run only after successful commit before cutover

- GIVEN a local registration attempt with email `alice@example.com` before the approved cutover
- WHEN the transaction commits successfully
- THEN the system SHALL publish `UserRegistered` event for `alice@example.com`
- AND the system SHALL issue a refresh session for the new principal
- AND the response SHALL include an access token
-
- GIVEN a local registration attempt with email `bob@example.com` before the approved cutover
- AND a workspace provisioning failure occurs inside the transaction
- WHEN the transaction rolls back
- THEN the system SHALL NOT publish any `UserRegistered` event
- AND the system SHALL NOT issue any refresh session
- AND no user_identities, credentials, workspaces, or verification tokens SHALL persist for
  `bob@example.com`

#### Scenario: Local registration does not issue credentials after cutover

- GIVEN the approved human-authentication cutover is complete
- WHEN a client submits local registration credentials
- THEN Profile Tailors MUST NOT issue local access credentials or a local refresh-token cookie
