# Proposal: Private Beta Launch Readiness

## Overview

Convert Profile Tailors foundations into a reversible, evidence-backed private-beta gate. Waitlist, identity, tenancy, invitation, publishing, and operations primitives exist, but launch is not proven end to end. Coordinate DALLAY-520, DALLAY-555, DALLAY-556, DALLAY-557, DALLAY-558, and final gate DALLAY-559 while separating implementation checks from VPS/operator evidence.

## Changes

### Scope

#### In Scope

- Exercise waitlist activation and invite → delivery → acceptance → workspace membership → first-login.
- Prove publishing/scheduling operations: deployment, worker safe-off, failure/stale-job visibility, backup/restore, and rollback.
- Cover the invited-user journey through first publish/schedule, including failure and unavailable-capability states.
- Produce dated evidence records and an explicit DALLAY-559 go/no-go checklist.

#### Out of Scope

- Provider-verified, production-verified, or multi-user-verified claims. Publishing remains `USER_REPORTED_OPERATIONAL`.
- LinkedIn Community Management, imported posts, comments/replies, webhooks, MCP, retention, or broad provider expansion.
- Unrelated refactors, tracker mutations, commits, or deployment-system replacement.

### Capabilities

#### New Capabilities

- `private-beta-launch-readiness`: evidence ledger, cohort gate, acceptance checklist, and reversible go/no-go process.

#### Modified Capabilities

- `lead-capture-waitlist`: independently exercise production activation and invitation conversion.
- `iam`: preserve authentication, membership, and tenancy boundaries during invited activation.
- `publishing`: add operational controls and evidence boundaries to beta acceptance.
- `e2e`: add the coordinated invitee journey and publish/schedule failure coverage.
- Managed ingress and readiness documentation: identify the production route in acceptance evidence.

### Approach

Use dependency-safe slices: (A) DALLAY-520, then DALLAY-556 invitation/membership; (B) DALLAY-555 publishing evidence and DALLAY-557 health/rollback/backup in parallel; (C) DALLAY-558 after A and minimum B evidence; (D) DALLAY-559 only after every prerequisite has a dated, classified record. Combine focused tests, BDD/E2E results, and operator/VPS evidence without conflating them. Never label evidence provider-verified or `MULTI_USER_VERIFIED`.

### Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `server/smp/src/{main,test}/kotlin/.../{leadcapture,platformadmin,identity,tenancy,publishing,platform}` | Modified | Activation, invitation, tenancy, worker, health, and regression coverage. |
| `apps/web/app/e2e/specs/` | Modified | Invitee activation and publishing journey coverage. |
| `docs/infrastructure/`, `docs/compliance/`, `infra/` | Modified | Runbooks, evidence, backup/restore, rollback, and safe-off procedures. |

## Usage

### Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| VPS/provider evidence is not reproducible | High | Record source, timestamp, scope, and classification; block rather than infer readiness. |
| Invitation, membership, or first login breaks | Med | Verify the complete chain and retain revoke/rollback procedures. |
| Worker failures become silent or duplicate | Med | Require lifecycle logs, stale-job checks, safe-off, and regression tests. |
| Work exceeds one reviewable change | Med | Keep autonomous slices and dependency order. |

### Dependencies

- Existing waitlist, invitation, IAM/tenancy, publishing, health, deployment, monitoring, and test infrastructure.
- Access to the managed beta VPS and operator-controlled backup/rollback mechanisms.

### Success Criteria

- [ ] DALLAY-520 and DALLAY-555–DALLAY-558 have passing checks and dated evidence.
- [ ] An invitee reaches first login and the documented publish/schedule journey; failure and unavailable states are explicit.
- [ ] DALLAY-559 has a complete go/no-go record; publishing remains `USER_REPORTED_OPERATIONAL`, never provider-verified or `MULTI_USER_VERIFIED`.
- [ ] Invitations and publishing can be safely disabled and prior deployment/data state restored.

## Troubleshooting

### Rollback Plan

Disable new invitations and publishing worker execution, revoke outstanding invitations, restore the last known-good deployment/configuration, and use documented backup/restore. Revert implementation separately from runbook/evidence changes; retain historical evidence.

## References

- DALLAY-520, DALLAY-555, DALLAY-556, DALLAY-557, DALLAY-558, and DALLAY-559.
- Existing waitlist, invitation, IAM/tenancy, publishing, deployment, monitoring, and test infrastructure.
