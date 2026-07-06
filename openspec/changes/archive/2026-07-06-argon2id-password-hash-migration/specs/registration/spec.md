# Delta for Registration

## MODIFIED Requirements

### Requirement: Registration Persists Atomically

(Previously: the transaction included identity creation, local password credential creation, default
workspace provisioning, and email verification token creation, without specifying password
algorithm metadata.)

The system MUST execute all persistent mutations for one registration attempt inside one atomic
transaction.

The transaction MUST include identity creation, local password credential creation, default
workspace provisioning, and email verification token creation. The local password credential
creation MUST persist both the password hash and the authoritative password hash algorithm metadata.
If any in-transaction mutation fails, the system MUST roll back all registration mutations from that
attempt. Partial user, credential, workspace, membership, role, verification-token, or password
algorithm metadata records MUST NOT remain committed after rollback.

#### Scenario: Registration commits Argon2id credential metadata atomically

- GIVEN a new user submits a valid registration payload
- WHEN all registration mutations succeed inside one transaction
- THEN the system MUST commit the local password credential with an Argon2id hash
- AND the committed credential MUST include `argon2id` algorithm metadata
- AND the user, credential, workspace, membership, role, and verification token MUST all persist

#### Scenario: Registration failure rolls back Argon2id credential metadata

- GIVEN a new user submits a valid registration payload
- AND one registration mutation fails before commit
- WHEN the transaction completes with failure
- THEN the system MUST roll back the local password credential write
- AND the system MUST roll back any associated password algorithm metadata
- AND no partial registration records MUST remain persisted
