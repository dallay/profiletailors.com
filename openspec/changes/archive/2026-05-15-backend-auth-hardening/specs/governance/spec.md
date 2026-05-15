# Delta for Governance

## MODIFIED Requirements

### Requirement: Auditability of Security-Relevant Platform Actions

The system MUST provide auditability for security-relevant platform actions and decisions as a platform concern.

At minimum, the platform MUST preserve a seam for recording security-relevant events involving authentication, credential use, workspace membership changes, role or grant changes, and protected authorization outcomes.
For `backend-auth-hardening`, the platform MUST produce runtime audit-ready proof for both allow and deny outcomes of the existing `/api/authorization/workspace-access/current` slice.
That proof MUST be attributable to explicit decision facts for the evaluated request, including the protected capability and enough authorization context to distinguish allow from deny for the active workspace request.
Phase-one hardening MUST remain limited to runtime proof for this existing slice and MUST NOT require audit persistence, compliance reporting, or broader governance workflows.
Comprehensive compliance reporting, retention operations, organization-wide governance workflows, and governance expansion beyond this slice remain deferred.

(Previously: The system provided auditability as a platform concern, preserved a seam for security-relevant events, and stated that phase one SHOULD capture the minimal events necessary to make the proving slice diagnosable while deferring comprehensive governance breadth.)

#### Scenario: Allowed workspace access outcome is audit-ready at runtime

- GIVEN an authenticated USER principal is allowed to access `/api/authorization/workspace-access/current`
- WHEN the protected request completes successfully
- THEN the platform MUST surface runtime audit-ready proof that the authorization outcome was allowed
- AND the proof MUST be attributable to the existing protected workspace-access slice rather than to a generic success outcome only

#### Scenario: Denied workspace access outcome is audit-ready at runtime

- GIVEN an authenticated USER principal is denied access to `/api/authorization/workspace-access/current`
- WHEN the protected request is evaluated
- THEN the platform MUST surface runtime audit-ready proof that the authorization outcome was denied
- AND the proof MUST be attributable to explicit decision facts for that protected slice

## ADDED Requirements

### Requirement: Current-Slice Governance Deferral Boundary

The system MUST keep governance scope for `backend-auth-hardening` limited to runtime proof for the existing workspace-access slice.

This change MUST NOT require durable audit storage, audit query APIs, compliance dashboards, policy administration, or governance coverage for endpoints beyond `/api/authorization/workspace-access/current`.
If broader governance capabilities are needed, they MUST be specified in a later change.

#### Scenario: Broader governance feature is deferred

- GIVEN a requested governance capability does not directly provide runtime audit-ready allow/deny proof for `/api/authorization/workspace-access/current`
- WHEN the hardening change scope is reviewed
- THEN that capability MUST be considered deferred
- AND the current change MUST proceed without broadening into phase-2 governance features

#### Scenario: Runtime proof is sufficient for this hardening phase

- GIVEN the existing workspace-access slice can emit audit-ready allow and deny proof at runtime
- AND the same slice is verified against PostgreSQL as required by this change
- WHEN completion of `backend-auth-hardening` is evaluated
- THEN the governance requirement for this change MUST be considered satisfied
- AND no additional governance expansion MUST be required for this phase
