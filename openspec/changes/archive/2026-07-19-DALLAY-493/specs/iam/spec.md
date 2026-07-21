# Delta for IAM — PII Anonymization and Correction Support

## ADDED Requirements

### Requirement: User Identity PII Anonymization

The Identity context MUST support anonymization of PII fields in `user_identities` (`email`, `username` → `[REDACTED on {timestamp}]`) and `principals.display_identity` (→ `[REDACTED]`). The operation MUST be idempotent: calling anonymize on an already-anonymized record MUST NOT fail.

#### Scenario: Anonymize replaces PII fields

- GIVEN a `user_identities` row with `email = "user@example.com"` and `username = "johndoe"`
- WHEN `anonymizePii(principalId)` is called
- THEN `email` MUST be `[REDACTED on 2026-07-19T12:00:00Z]`
- AND `username` MUST be `[REDACTED on 2026-07-19T12:00:00Z]`
- AND `principals.display_identity` MUST be `[REDACTED]`

#### Scenario: Double anonymization is idempotent

- GIVEN a `user_identities` row already anonymized
- WHEN `anonymizePii(principalId)` is called again
- THEN the operation MUST succeed without error

### Requirement: PII Correction Through CQRS

The Identity context MUST expose a `CorrectUserIdentityCommand` handler for `email` and `username`. Validation: `email` MUST match RFC 5322; `username` MUST be 3–50 alphanumeric characters. The handler MUST return old values as a snapshot for rollback.

#### Scenario: Correction validates email format

- GIVEN a CORRECTION request with `email = "not-an-email"`
- WHEN validation is applied
- THEN the system MUST reject with `invalid_email`

#### Scenario: Correction returns old values

- GIVEN a CORRECTION from `"old@x.com"` to `"new@x.com"`
- WHEN the handler completes
- THEN it MUST return a snapshot containing `"old@x.com"`
