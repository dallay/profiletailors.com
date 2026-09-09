# Delta for Invitations

## ADDED Requirements

### Requirement: Validate invitation before mutation

Registration SHALL validate token, lifecycle, exclusive expiry, and normalized target email before mutation. Raw tokens SHALL remain in-process only and SHALL NOT be persisted, returned, logged, audited, or measured. Invalid, expired, revoked, consumed, replayed, and mismatched attempts SHALL have deterministic outcomes. The HTTP contract is defined as Problem Details with stable application codes: invalid/unknown `400 INVITATION_INVALID`; expired `410 INVITATION_EXPIRED`; revoked `410 INVITATION_REVOKED`; consumed `409 INVITATION_ALREADY_CONSUMED`; replay `409 INVITATION_REPLAYED` only when the domain distinguishes the attempt, otherwise consumed classification plus `replay_rejected` telemetry; email mismatch `403 INVITATION_EMAIL_MISMATCH`; client workspace input `400 INVITATION_WORKSPACE_OVERRIDE_NOT_ALLOWED`. Details remain generic and omit token, full email, and workspace values.

#### Scenario: Valid invitation

- GIVEN invite-only mode, an active unexpired invitation, and matching normalized email
- WHEN registration supplies the valid token and required consent
- THEN the server SHALL enter atomic acceptance and link the principal

#### Scenario: Invalid or no-longer-valid invitation

- GIVEN an invalid, expired, revoked, consumed, replayed, or mismatched token
- WHEN registration supplies it
- THEN the decided deterministic rejection SHALL occur before account or invitation mutation

### Requirement: Invitation target determines workspace

`EXISTING_WORKSPACE` SHALL use its stored workspace. `NEW_WORKSPACE` SHALL provision exactly one workspace for the new principal and associate it with the accepted invitation. The server SHALL NOT authorize or route from a client-selected workspace, invitation ID, or fallback identity.

#### Scenario: Existing or new target

- GIVEN a valid invitation targeting existing workspace `W` or a new workspace
- WHEN a new identity registers
- THEN membership SHALL use `W`, or exactly one workspace SHALL be provisioned and linked

#### Scenario: Client workspace override

- GIVEN a valid invitation and a request containing another workspace ID
- WHEN registration is processed
- THEN the server SHALL reject the request with `400 INVITATION_WORKSPACE_OVERRIDE_NOT_ALLOWED`
- AND workspace SHALL be derived only from the invitation

### Requirement: Existing identity is authenticated and non-duplicating

If the normalized invitation email belongs to an identity, acceptance SHALL require authentication as that identity and an exact normalized-email match. It SHALL create no duplicate account or credential. Verification follows the existing registration policy.

#### Scenario: Existing identity outcome

- GIVEN an existing identity and valid matching invitation
- WHEN that identity, an unauthenticated principal, or a differently emailed principal accepts
- THEN only the authenticated matching identity may proceed; no duplicate or failed-path mutation SHALL occur

### Requirement: Acceptance mutations are atomic

Identity, credentials, required consent, policy-required verification state, workspace provisioning or membership, principal linkage, and invitation acceptance SHALL commit or roll back together. Failure SHALL leave no partial account or consumed invitation. Session issuance SHALL follow commit.

#### Scenario: Commit and rollback

- GIVEN all validations pass
- WHEN registration succeeds or a credential, consent, workspace, membership, or acceptance operation fails
- THEN either every required mutation commits or none remains

### Requirement: Verification follows existing policy

Invitation registration SHALL follow the existing verification policy and SHALL NOT add an invitation-specific gate or dynamic registration configuration. If policy does not require verification, this flow SHALL not require it.

#### Scenario: Existing policy

- GIVEN invitation registration and the existing verification policy
- WHEN the account is created
- THEN its verification state SHALL exactly follow that policy

### Requirement: Evidence is redacted and aggregate

Successful acceptance SHALL emit auditable invitation identity, outcome, timestamps, and approved correlation data only. Acceptance, expiry, revoke, and replay-rejection metrics SHALL use bounded low-cardinality dimensions. Evidence SHALL exclude raw tokens, token URLs, passwords, consent payloads, and full emails.

#### Scenario: Safe evidence

- GIVEN a committed or rejected acceptance attempt
- WHEN audit or metrics evidence is emitted
- THEN it SHALL contain only approved non-sensitive identifiers and aggregate outcome data

## MODIFIED Requirements

### Requirement: One-time concurrent acceptance

The acceptance contract SHALL atomically permit at most one complete registration acceptance per invitation. Under concurrent valid attempts, exactly one contender SHALL commit the account path and `ACTIVE`→`ACCEPTED` transition; losers SHALL receive the decided deterministic replay/no-longer-valid outcome and leave no partial account. (Previously: lifecycle transition was protected while provisioning remained outside this change.)

#### Scenario: Concurrent acceptance and replay

- GIVEN two valid attempts contend, or a token is submitted after committed acceptance
- WHEN processing finishes
- THEN exactly one complete account and accepted invitation SHALL persist; the loser/replay SHALL reject without mutation

## REMOVED Requirements

### Requirement: Token-presence authorization and split acceptance path

(Reason: authorization SHALL use server validation, not token presence or a separate token-only acceptance path.)
