# Delta for Governance

## MODIFIED Requirements

### Requirement: Auditability of Security-Relevant Platform Actions

The system MUST provide auditability for security-relevant platform actions and decisions as a platform concern.

At minimum, the platform MUST preserve a seam for recording security-relevant events involving authentication, credential use, workspace membership changes, role or grant changes, and protected authorization outcomes.
For the existing proving slice, the platform MUST produce runtime audit-ready proof for allow and deny outcomes of `/api/authorization/workspace-access/current` for authenticated USER, authenticated SERVICE_ACCOUNT, and authenticated API_KEY requests.
That proof MUST be attributable to explicit decision facts for the evaluated request, including the protected capability and enough authorization or credential-state context to distinguish allow, authorization-controlled deny, revoked-or-inactive API-key deny, predecessor-after-replacement deny, and successor-after-replacement allow on the active workspace request.
This change MUST remain limited to runtime proof for the existing slice and MUST NOT require audit persistence, compliance reporting, credential-governance dashboards, issuance reporting, inventory reporting, or broader governance workflows.
The current change MUST require end-to-end before-and-after proof on `/api/authorization/workspace-access/current` showing that the old API key is accepted before replacement, the new API key is accepted after replacement, and the old API key is denied after replacement completes.
Comprehensive compliance reporting, retention operations, organization-wide governance workflows, and governance expansion beyond this slice remain deferred.

(Previously: Runtime proof had to distinguish allow, authorization-controlled deny, and revoked-or-inactive API-key deny on the active workspace request.)

#### Scenario: Successor API-key allow is audit-ready at runtime after replacement

- GIVEN an authenticated API_KEY principal accesses `/api/authorization/workspace-access/current` with a successor API key after a completed replacement
- WHEN the protected request completes successfully
- THEN the platform MUST surface runtime audit-ready proof that the authorization outcome was allowed
- AND the proof MUST make the successor-after-replacement outcome attributable to explicit decision facts for the existing slice

#### Scenario: Predecessor API-key denial is audit-ready at runtime after replacement

- GIVEN a persisted API_KEY principal presents a predecessor API key for `/api/authorization/workspace-access/current`
- AND a completed replacement has made that credential the predecessor of an accepted successor
- WHEN the request is evaluated
- THEN the platform MUST surface runtime audit-ready proof that the outcome was denied because the predecessor credential was no longer valid after replacement
- AND the proof MUST remain limited to runtime observability for the existing slice

#### Scenario: Broader lifecycle governance remains deferred

- GIVEN a requested governance capability requires credential inventory reporting, durable audit storage, or generalized lifecycle dashboards
- WHEN the scope for this change is reviewed
- THEN that capability MUST be considered deferred
- AND the current change MUST proceed without broadening beyond runtime proof for the replacement cutover on the existing slice
