# Delta for IAM

## MODIFIED Requirements

### Requirement: Credential Mechanisms

(Previously: phase one defined JWT, refresh credentials, service-account bearer credentials, API keys,
and email verification gating, but did not specify password-hash algorithm migration behavior for
local email/password credentials.)

The system MUST support multi-algorithm verification for local email/password credentials during the
BCrypt-to-Argon2id migration.

New local password registrations MUST persist an Argon2id password hash.
The system MUST preserve login compatibility for existing BCrypt password hashes.
The system MUST persist explicit algorithm metadata for local password credentials in authoritative
backend state when storing new or upgraded password hashes.
If a successful login authenticates against a legacy BCrypt hash, the system MUST opportunistically
replace the stored credential with an Argon2id hash after the password is verified successfully.
If algorithm metadata is absent or stale, the system SHALL infer the algorithm defensively from the
stored hash format only for compatibility and rollback-safe handling.
Malformed or unsupported password hashes MUST fail closed as invalid credentials and MUST NOT
surface as user-visible 500 responses.

#### Scenario: New registration stores Argon2id credential

- GIVEN a new user submits a valid local registration request
- WHEN the backend persists the local password credential
- THEN the stored password hash MUST be Argon2id
- AND the stored credential metadata MUST indicate `argon2id`

#### Scenario: Existing BCrypt credential remains login-compatible

- GIVEN a user exists with a valid BCrypt password hash
- WHEN the user submits the correct password to the login endpoint
- THEN the system MUST authenticate the user successfully
- AND the system MUST preserve normal token issuance behavior for that login

#### Scenario: Successful BCrypt login triggers rehash

- GIVEN a user exists with a valid BCrypt password hash and compatible principal identity
- WHEN the user authenticates successfully with the correct password
- THEN the system MUST replace the stored password hash with a new Argon2id hash
- AND the stored credential metadata MUST be updated to `argon2id`

#### Scenario: Missing algorithm metadata falls back safely

- GIVEN a local password credential row has no explicit algorithm metadata
- AND the stored hash format is recognizable as BCrypt
- WHEN the user authenticates successfully
- THEN the system SHALL accept the legacy credential
- AND the system SHALL upgrade the stored credential to Argon2id metadata and hash

#### Scenario: Malformed hash fails closed

- GIVEN a local password credential row contains malformed or unsupported hash data
- WHEN the user submits a login request
- THEN the system MUST reject the login as invalid credentials
- AND the system MUST NOT return an internal server error
