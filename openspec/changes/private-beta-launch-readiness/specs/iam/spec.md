# Delta for IAM

## Overview

This delta defines the invitation acceptance boundary for DALLAY-556: validate the invitation before membership changes, preserve workspace isolation, protect invitation secrets and PII, and keep production evidence distinct from automated behavior evidence.

## Changes

### Requirement: Invite Acceptance Creates Scoped Membership (DALLAY-556)

An invitation acceptance flow MUST validate the invitation before creating membership. A valid acceptance MUST create or reconcile exactly one membership in the invited workspace, preserve the workspace boundary, and allow the invitee to complete first login. An invite MUST NOT grant access to any other workspace or bypass existing authentication, email-verification, or authorization gates.

#### Scenario: Invitee accepts and reaches first login

- GIVEN a valid, unexpired invitation for workspace A and an authenticated or newly registered invitee
- WHEN the invitee accepts the invitation and completes the required login step
- THEN exactly one membership for workspace A MUST exist
- AND the invitee MUST be able to load workspace-scoped data for A
- AND the acceptance record MUST be attributable to the invitation and actor

#### Scenario: Invalid or expired invitation is rejected

- GIVEN an invitation token is missing, altered, expired, revoked, or already consumed
- WHEN the invitee attempts acceptance
- THEN the system MUST reject the request
- AND MUST NOT create or change workspace membership
- AND the response MUST expose no token validity detail beyond the safe error contract

#### Scenario: Cross-workspace access remains denied

- GIVEN an invitee is a member of workspace A but not workspace B
- WHEN the invitee requests a workspace-B resource
- THEN authorization MUST deny the request
- AND no data, membership, or invitation state in workspace B may be disclosed

### Requirement: Invitation Secrets and PII Are Protected

Invitation tokens MUST be single-use, time-bounded, and treated as secrets. API responses, browser logs, screenshots, and evidence MUST NOT expose raw tokens, credentials, or unnecessary invitee PII. The system MUST preserve existing deny-by-default and explicit workspace-context requirements.

#### Scenario: Consumed invitation cannot be replayed

- GIVEN an invitation was accepted successfully
- WHEN the same raw invitation token is submitted again
- THEN acceptance MUST be rejected
- AND the existing membership MUST remain unchanged

### Requirement: Invitation Evidence Does Not Prove Delivery By Itself

Unit, BDD, and integration tests MAY prove invitation validation, membership creation, and tenancy behavior. They MUST NOT be treated as proof that a production email was delivered. Only a dated, redacted operator record from the managed beta environment MAY establish observed invite delivery and acceptance; absent that record, the invitation prerequisite MUST remain unverified.

#### Scenario: Production invitation evidence has provenance

- GIVEN an operator exercises invite delivery and acceptance in the managed beta environment
- WHEN the evidence record is reviewed
- THEN it MUST identify UTC time, deployment/environment, scope, and observed outcome
- AND it MUST omit raw invitation tokens, credentials, and full invitee PII

## Usage

### Evidence Classification

Classify automated implementation evidence as code or test evidence and managed-environment observations as operator evidence. Do not infer production delivery from a passing local, CI, BDD, or integration test.

## Troubleshooting

### Acceptance Blockers

Missing provenance, exposed secrets or unnecessary PII, invalid invitation state, cross-workspace access, or an unavailable rollback path blocks the invitation prerequisite.

## References

- DALLAY-556.
- Existing identity, tenancy, invitation, authorization, and evidence-ledger contracts.
