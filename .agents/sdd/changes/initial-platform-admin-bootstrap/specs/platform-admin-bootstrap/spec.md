# Platform Admin Bootstrap Specification

## Purpose

Specify secure first-owner setup.

## Requirements

### Requirement: First-owner invitation issuance

A private, explicitly invoked interactive in-container command MUST issue invitations only when no active owner exists. The operator MUST interactively enter and confirm normalized email; existing-account emails MUST be rejected without mutation or elevation. The command MUST take no password via arguments/environment, store only a one-use 30-minute token hash, print its link once to interactive stdout, and never email it. Replacements MUST be interactively confirmed, transactional, and concurrency-safe. Existing or uncertain owner state MUST fail closed. Normal startup MUST NOT invoke the command.

#### Scenario: Issue

- GIVEN no active owner and no pending invitation
- WHEN the operator issues for confirmed normalized email
- THEN a one-use invitation expires in 30 minutes and is hash-only
- AND one link prints to stdout, not email

#### Scenario: Existing email

- GIVEN the confirmed email belongs to an account
- WHEN the operator issues an invitation
- THEN issuance is rejected without mutation or elevation

#### Scenario: Replace

- GIVEN an invitation is pending
- WHEN the operator confirms replacement
- THEN the prior invitation is invalidated transactionally
- AND concurrency leaves at most one valid invitation

#### Scenario: Owner state

- GIVEN an owner exists or status is uncertain
- WHEN issuance is attempted
- THEN issuance fails closed without mutation

### Requirement: Owner grant gates

Recipient MUST claim the invitation before its fixed 30-minute expiry when creating the identity and password. The claim MUST bind the invitation to that principal in a non-reassignable pending-verification state; the existing email-verification token expiry then governs the remaining verification period. The recipient MUST verify email and explicitly accept the current Terms version before `PLATFORM_OWNER` grant. The recipient MUST be given the current Terms version for review before acceptance. Record acceptance as consent in the ID-free `PLATFORM` scope, without a workspace, waitlist, or other entity identifier. Bootstrap MUST NOT require age-eligibility confirmation, elevate accounts, or grant customer access. Normal registration Terms and age rules MUST remain unchanged. Consume the invitation once at most.
(Previously: verification gated the grant and age confirmation was excluded, but terms acceptance was unresolved.)

#### Scenario: Accepted terms

- GIVEN password set, email verified, and the current Terms version reviewed and explicitly accepted
- WHEN bootstrap completes
- THEN `PLATFORM_OWNER` is granted only after all checks
- AND `PLATFORM` consent is recorded without a workspace, waitlist, or membership identifier
- AND customer access is not granted

#### Scenario: Terms missing/outdated

- GIVEN email is unverified or the current Terms version is not explicitly accepted
- WHEN completion is attempted
- THEN no owner grant or customer access occurs
- AND no workspace or membership is created

#### Scenario: Verification and owner grant are atomic

- GIVEN a bootstrap verification token is valid and role assignment or audit persistence fails
- WHEN email verification attempts to grant the owner role
- THEN verification-token consumption, email status, invitation completion, role assignment, and audit are rolled back together
- AND the recipient can retry with the unconsumed verification token

#### Scenario: Existing or invalid invite

- GIVEN an existing account, used invitation, or invitation expired before claim
- WHEN completion is attempted
- THEN no owner grant or customer access occurs

#### Scenario: Customer behavior

- GIVEN a customer uses an existing registration endpoint
- WHEN the customer completes registration
- THEN the endpoint contract and customer Terms and age-eligibility behavior are unchanged

### Requirement: Bootstrap route

The admin SPA MUST expose public `/bootstrap` only for recipient completion. The flow MUST provide the current Terms version for recipient review before acceptance. Public responses MUST NOT reveal whether an invitation exists or its status, including for missing, invalid, used, or expired invitation credentials; issuance and status MUST remain private. Bootstrap MUST NOT add public/customer issuance/status APIs or modify existing customer endpoints, their contracts, or their behavior, and MUST NOT change readiness. Links and backend CORS MUST use the installation's runtime-configured admin origin; no specific domain is required.

#### Scenario: Open bootstrap

- GIVEN a valid invitation exists
- WHEN the recipient opens `/bootstrap` on the configured admin origin
- THEN unauthenticated completion is available, not issuance/status
- AND the current Terms version is provided for review before acceptance
- AND password setup uses existing verification email delivery

#### Scenario: Unusable invitation

- GIVEN the invitation credential is missing, invalid, used, or expired
- WHEN the recipient opens `/bootstrap` or submits completion
- THEN the public response does not reveal whether an invitation exists or its status
- AND no owner grant occurs

#### Scenario: Status/readiness

- GIVEN an invitation is pending
- WHEN an unauthenticated customer requests either
- THEN status is hidden and readiness remains independent

#### Scenario: Customer endpoints

- GIVEN a customer uses an existing endpoint
- WHEN the bootstrap flow is available
- THEN the endpoint's contract and behavior remain unchanged

### Requirement: Safe initialization

Audits MUST include available actor, normalized email, action, IDs, time, result, and reason. Audits, logs, and responses MUST exclude passwords, raw tokens, links, and token hashes. Clean initialization MUST need no owner, workspace, or membership rows and enforce token one-use and expiry.

#### Scenario: Audit

- GIVEN issuance or completion has an outcome
- WHEN the event is audited
- THEN required metadata is recorded without passwords, tokens, links, or hashes

#### Scenario: Initialize

- GIVEN required migrations are applied to a new installation
- WHEN bootstrap storage is initialized
- THEN bootstrap needs no owner, workspace, or membership rows
- AND invalid or duplicate token state is rejected
