# Governance Specification

## Purpose

Define governance and audit semantics for the reusable IAM platform. This specification establishes
the platform requirements for auditability, policy traceability, and governance-ready authorization
behavior while keeping operational breadth phased.

## Requirements

### Requirement: Auditability of Security-Relevant Platform Actions

The system MUST provide auditability for security-relevant platform actions and decisions as a platform concern.

At minimum, the platform MUST preserve a seam for recording security-relevant events involving authentication, credential use, workspace membership changes, role or grant changes, and protected authorization outcomes.
For hardening of the existing proving slice, the platform MUST produce runtime audit-ready proof for both allow and deny outcomes of `/api/authorization/workspace-access/current`.
That proof MUST be attributable to explicit decision facts for the evaluated request, including the protected capability and enough authorization context to distinguish allow from deny for the active workspace request.
Phase-one hardening MUST remain limited to runtime proof for this existing slice and MUST NOT require audit persistence, compliance reporting, or broader governance workflows.
Comprehensive compliance reporting, retention operations, organization-wide governance workflows, and governance expansion beyond this slice remain deferred.

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

### Requirement: Current-Slice Governance Deferral Boundary

The system MUST keep governance scope for hardening of the existing workspace-access slice limited to runtime proof for that slice.

This change scope MUST NOT require durable audit storage, audit query APIs, compliance dashboards, policy administration, or governance coverage for endpoints beyond `/api/authorization/workspace-access/current`.
If broader governance capabilities are needed, they MUST be specified in a later change.

#### Scenario: Broader governance feature is deferred

- GIVEN a requested governance capability does not directly provide runtime audit-ready allow/deny proof for `/api/authorization/workspace-access/current`
- WHEN the hardening change scope is reviewed
- THEN that capability MUST be considered deferred
- AND the current change MUST proceed without broadening into phase-2 governance features

#### Scenario: Runtime proof is sufficient for this hardening phase

- GIVEN the existing workspace-access slice can emit audit-ready allow and deny proof at runtime
- AND the same slice is verified against PostgreSQL as required by the hardening change
- WHEN completion of that hardening scope is evaluated
- THEN the governance requirement for that change MUST be considered satisfied
- AND no additional governance expansion MUST be required for that phase


### Requirement: Deterministic and Explainable Authorization Governance

The system MUST support governance through deterministic and explainable authorization behavior.

Authorization outcomes MUST be attributable to explicit platform facts such as membership, role
permissions, direct grants, denials, scopes, entitlements, and context.
The platform MUST favor explicit-over-implicit behavior so decisions can be understood and governed.
The system MUST NOT rely on undocumented fallback rules for protected access.

#### Scenario: Denial is explainable from explicit facts

- GIVEN a principal is denied a protected capability
- WHEN the authorization outcome is examined
- THEN the denial MUST be attributable to explicit platform facts or the lack of an explicit allow
  path
- AND the result MUST NOT depend on hidden or undocumented fallback behavior

#### Scenario: Equivalent state yields equivalent governed outcome

- GIVEN two identical protected requests are evaluated against equivalent authoritative platform
  state
- WHEN authorization is resolved
- THEN the platform MUST produce the same outcome for both
- AND governance review MUST be able to reason about the decision deterministically

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
