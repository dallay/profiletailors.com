# Delta for Email Verification

## ADDED Requirements

### Requirement: Bootstrap owner role waits for verified email

A bootstrap recipient MUST claim the invitation before its fixed 30-minute expiry while setting a password, then complete the existing email-verification flow before the system grants `PLATFORM_OWNER`. Bootstrap MUST NOT elevate an existing account. Invitation claim and verification MUST be single-use. An invitation expired before claim MUST NOT authorize a role grant; after claim, the existing email-verification token expiry governs verification.

#### Scenario: Verification gates owner grant

- GIVEN a new account has set its password using a valid bootstrap invitation
- WHEN email verification succeeds
- THEN `PLATFORM_OWNER` is granted only after verification succeeds
- AND the consumed invitation cannot grant the role again

#### Scenario: Verification failure or expiry prevents grant

- GIVEN verification fails, its token has expired, or the invitation expired before claim
- WHEN the recipient attempts setup or verification
- THEN the account receives no `PLATFORM_OWNER` role
- AND no workspace or membership is created
