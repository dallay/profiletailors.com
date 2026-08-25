# Private Beta Launch Readiness Specification

## Purpose

Define the evidence-backed, reversible go/no-go gate for the private beta. The gate coordinates
DALLAY-520, DALLAY-555, DALLAY-556, DALLAY-557, DALLAY-558, and final DALLAY-559 without conflating
implemented behavior, automated test results, managed-VPS observations, and user reports.

## Requirements

### Requirement: Evidence Ledger Has Explicit Boundaries

Every prerequisite MUST have a UTC timestamp, environment, deployment/revision identity when relevant,
scope, source, observed result, owner, and classification: `CODE_VERIFIED`, `TEST_VERIFIED`,
`VPS_OBSERVED`, `OPERATOR_REPORTED`, or `UNVERIFIED`. Code/test evidence MUST describe code behavior
only. VPS evidence MUST describe only the observed managed instance. Publishing outcomes MUST remain
`USER_REPORTED_OPERATIONAL` unless separate provider evidence exists; this gate MUST NOT create provider
or `MULTI_USER_VERIFIED` claims.

#### Scenario: Evidence is accepted with provenance

- GIVEN a prerequisite record has all required fields and no secret or unnecessary PII
- WHEN the gate evaluates it
- THEN the record MUST be eligible for prerequisite review
- AND its classification MUST remain visible to the operator

#### Scenario: Ambiguous evidence blocks launch

- GIVEN a record is missing provenance, stale, contradictory, or marked `UNVERIFIED`
- WHEN DALLAY-559 is evaluated
- THEN the gate MUST return `NO-GO`
- AND the missing evidence MUST be listed as a launch blocker

### Requirement: Final Gate Requires Every Prerequisite

DALLAY-559 MUST evaluate dated evidence for waitlist activation/conversion, invite acceptance and
membership, publishing failure/stale visibility, worker safe-off, route/readiness, backup/restore,
rollback, and the invitee E2E journey. A failed privacy/security check, unsafe cross-workspace access,
exposed secret, unavailable rollback, or missing recovery action MUST be a launch blocker.

#### Scenario: Complete gate returns GO

- GIVEN DALLAY-520 and DALLAY-555 through DALLAY-558 each have passing, classified evidence
- AND rollback, safe-off, backup/restore, privacy, and route checks pass
- WHEN the operator records DALLAY-559
- THEN the result MUST be `GO`
- AND the record MUST list all limitations, including `USER_REPORTED_OPERATIONAL` publishing

#### Scenario: One prerequisite fails

- GIVEN any required issue has a failing, missing, or blocked acceptance criterion
- WHEN the final gate is recorded
- THEN the result MUST be `NO-GO`
- AND invitations or publishing MUST remain disabled or safely reversible until remediation

### Requirement: Rollback and Evidence Handling Are Safe

The operator MUST be able to disable new invitations and worker execution, revoke outstanding
invitations, restore the last known-good deployment/data state, and retain historical evidence. Evidence
MUST exclude tokens, credentials, provider secrets, raw payloads, and unnecessary customer content.

#### Scenario: Reversible gate failure

- GIVEN the final gate is `NO-GO` after a beta exercise
- WHEN the documented containment procedure is executed
- THEN new invitations and worker delivery MUST stop
- AND the operator MUST record the restored state and remaining risks
