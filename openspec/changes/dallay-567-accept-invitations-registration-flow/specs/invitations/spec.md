# Delta for Invitations

## ADDED Requirements

### Requirement: Validate invitation before mutation

Registration MUST validate token, lifecycle, expiry, and normalized target email before mutation. It
MUST return Problem Details: invalid `400 INVITATION_INVALID`; expired `410
INVITATION_EXPIRED`; revoked `410 INVITATION_REVOKED`; consumed or replayed `409
INVITATION_ALREADY_CONSUMED` or `INVITATION_REPLAYED`; mismatch `403 INVITATION_EMAIL_MISMATCH`;
and client workspace override `400 INVITATION_WORKSPACE_OVERRIDE_NOT_ALLOWED`. Details MUST omit
tokens, full emails, and workspace values.

#### Scenario: QA-06 expired invite

- GIVEN an invitation at or beyond its exclusive `expiresAt` boundary
- WHEN its matching token is submitted
- THEN HTTP `410 INVITATION_EXPIRED` is returned and no mutation remains

#### Scenario: QA-07 revoked invite

- GIVEN an invitation whose lifecycle is `REVOKED`
- WHEN its matching token is submitted
- THEN HTTP `410 INVITATION_REVOKED` is returned and no mutation remains

#### Scenario: QA-08 email mismatch

- GIVEN an active invitation targeted to normalized email A
- WHEN its token is submitted with normalized email B
- THEN HTTP `403 INVITATION_EMAIL_MISMATCH` is returned without mutation or sensitive details

### Requirement: Invitation target determines workspace

`EXISTING_WORKSPACE` MUST use its stored workspace. `NEW_WORKSPACE` MUST provision exactly one
workspace for the principal. Client workspace, invitation ID, or fallback identity
MUST NOT determine authorization or tenancy.

#### Scenario: Existing or new target

- GIVEN a valid invitation targeting existing workspace W or a new workspace
- WHEN a new identity accepts
- THEN membership uses W, or exactly one workspace is provisioned and linked

#### Scenario: Workspace override

- GIVEN a valid invitation and a client workspace ID
- WHEN registration is processed
- THEN `400 INVITATION_WORKSPACE_OVERRIDE_NOT_ALLOWED` is returned before acceptance dispatch

### Requirement: Existing identity is authenticated and non-duplicating

If an invitation email belongs to an identity, acceptance MUST require authentication as that
identity and exact normalized-email equality. It MUST reuse the existing identity and credential;
verification and membership follow policy without duplicates.

#### Scenario: QA-09 matching existing identity

- GIVEN `principal-1` is authenticated, owns the invitation email, and has its credential
- WHEN it accepts an existing-workspace invitation
- THEN no duplicate identity, credential, or membership is created and one membership exists

### Requirement: Acceptance mutations are atomic

Identity, credentials, consent, verification state, workspace or membership, principal linkage, and
invitation acceptance MUST commit or roll back together. Session issuance MUST follow commit.

#### Scenario: Commit and rollback

- GIVEN validation succeeds
- WHEN acceptance succeeds or a later required mutation fails
- THEN mutations commit, or none remain

### Requirement: Verification follows existing policy

Invitation registration MUST use the existing verification policy and MUST NOT add an invitation-
specific gate or dynamic configuration.

#### Scenario: Existing verification policy

- GIVEN the registration verification policy
- WHEN invitation registration succeeds
- THEN its verification state exactly follows that policy

### Requirement: Evidence is redacted and aggregate

Acceptance, expiry, revoke, and replay evidence MUST use approved identifiers, outcomes, timestamps,
and bounded dimensions. Raw tokens, token URLs, passwords, consent payloads, and full emails
MUST NOT be emitted.

#### Scenario: Safe evidence

- GIVEN a committed or rejected invitation attempt
- WHEN audit or metric evidence is emitted
- THEN only redacted identifiers and aggregate outcome data are present

## MODIFIED Requirements

### Requirement: One-time concurrent acceptance

The acceptance contract MUST atomically permit at most one complete registration acceptance per
invitation. Under concurrent valid attempts, exactly one contender MUST commit the account path and
`ACTIVE` to `ACCEPTED` transition; losers MUST receive the deterministic replay/no-longer-
valid outcome and leave no partial account. (Previously: lifecycle transition was protected while
provisioning remained outside this change.)

#### Scenario: Concurrent acceptance and replay

- GIVEN two valid attempts contend, or a token is submitted after committed acceptance
- WHEN processing finishes
- THEN exactly one complete acceptance persists and the loser or replay rejects without mutation

## REMOVED Requirements

### Requirement: Token-presence authorization and split acceptance path

(Reason: authorization MUST use server validation, not token presence or a separate token-only path.)
