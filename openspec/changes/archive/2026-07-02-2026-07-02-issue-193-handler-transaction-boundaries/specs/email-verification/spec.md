# Delta for Email Verification

## ADDED Requirements

### Requirement: Email Verification Consumes Token and Updates Status Atomically

The system MUST consume a verification token and update the associated email status within one
atomic persistence boundary. If either persistence mutation fails, both mutations SHALL roll back.
Successful verification MAY issue auth/session material only after the atomic persistence boundary
succeeds.

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

The system MUST invalidate prior verification tokens and create the replacement token within one
atomic persistence boundary. Verification email events SHALL be published only after successful
transaction commit.

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
