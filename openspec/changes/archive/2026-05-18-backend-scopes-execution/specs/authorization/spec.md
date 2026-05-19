# Delta for authorization

## ADDED Requirements

### Requirement: Target-Aware Resource Preview Proving Capability

The system MUST add exactly one new protected target-aware proving capability that evaluates authorization against an explicit `targetResourceId`.

For this change, the capability MUST follow a resource-preview-by-resourceId pattern in WORKSPACE context.
The capability MUST declare the explicit base permission `workspace:resource:read`.
The capability MUST evaluate the requested `targetResourceId` as authorization-relevant input for this capability.
This change MUST remain limited to this one target-aware proving capability and MUST NOT retrofit scope execution onto `/api/authorization/workspace-access/current`.

#### Scenario: Target-aware capability uses explicit base permission and target resource id

- GIVEN a principal requests the new resource-preview proving capability
- AND the request supplies an explicit active workspace identifier
- AND the request supplies an explicit `targetResourceId`
- WHEN authorization is evaluated for that capability
- THEN the platform MUST evaluate the request in WORKSPACE context
- AND it MUST require the explicit base permission `workspace:resource:read`
- AND it MUST evaluate the supplied `targetResourceId` as the target resource for scope reduction

### Requirement: Persisted Workspace-Scoped Target Scope for Resource Preview

The system MUST support one persisted workspace-scoped scope model that can reduce allowed target resource IDs for the new target-aware proving capability.

For this change, the persisted scope model MUST bind the active workspace, principal, base permission `workspace:resource:read`, and an allowed target resource-ID set or an equivalently narrow representation for this one capability.
The persisted scope model MUST be sufficient to decide whether the requested `targetResourceId` is inside the allowed reduced set for that capability.
This change MUST NOT require a generic scope engine, wildcard matching, inheritance, hierarchical target matching, multi-context scopes, admin CRUD, quotas, billing semantics, entitlement combinations, or broad policy redesign.

#### Scenario: Persisted scope narrows allowed target resource ids for the proving capability

- GIVEN a principal already has an otherwise valid allow path for `workspace:resource:read`
- AND the active workspace has persisted scope state for that principal and capability
- WHEN the principal requests the new resource-preview proving capability for a `targetResourceId`
- THEN the platform MUST evaluate the persisted scope as a reduction over allowed target resource IDs
- AND the result MUST be sufficient to allow or deny that target based on membership in the reduced allowed set

#### Scenario: Broader scope features remain deferred

- GIVEN a requested addition introduces wildcard scopes, inheritance, multi-context scope breadth, admin CRUD, or broad policy redesign
- WHEN the scope for `backend-scopes-execution` is evaluated
- THEN that addition MUST be treated as out of scope for this change
- AND the current change MUST proceed without requiring those broader scope features

### Requirement: Scope-Execution Change Boundary for the New Proving Capability

The system MUST treat `backend-scopes-execution` as a narrow executable scope-reduction change for one new target-aware proving capability only.

This change MUST include only the behaviors required to execute persisted workspace-scoped target reduction for the new resource-preview-by-resourceId capability.
This change MUST NOT require generic scope engines, wildcards, inheritance, multi-context scopes, admin CRUD, quotas, billing or entitlement combinations, or broad RBAC/ABAC and policy-platform redesign.
This change MUST NOT broaden scope execution to `/api/authorization/workspace-access/current` or to unrelated protected endpoints.

#### Scenario: Existing workspace-access-summary slice remains outside this scope change

- GIVEN a requested addition applies executable scope reduction to `/api/authorization/workspace-access/current`
- WHEN the scope for `backend-scopes-execution` is evaluated
- THEN that addition MUST be treated as out of scope for this change
- AND executable scope proof MUST remain tied to the new target-aware proving capability

#### Scenario: Generic scope-platform breadth is rejected

- GIVEN a proposed addition does not directly support the one new resource-preview proving capability
- WHEN the scope for `backend-scopes-execution` is reviewed
- THEN the addition MUST be treated as out of scope for this change
- AND the specification MUST keep the scope proof limited to the approved target-aware slice

## MODIFIED Requirements

### Requirement: Scopes Can Only Reduce Permissions

The system MUST support scopes as permission-reducing constraints.

A scope MUST narrow what an already otherwise-allowable principal may do.
A scope MUST NEVER create or expand permissions not already available through roles, direct grants, or future policy evaluation.
For `backend-scopes-execution`, scope evaluation for the new resource-preview proving capability MUST run only after an otherwise valid allow path exists for the explicit base permission `workspace:resource:read`.
For `backend-scopes-execution`, the scope result MUST only reduce which `targetResourceId` values remain reachable for that capability.
Phase one MAY defer generalized scope authoring and management, but authorization evaluation semantics MUST reserve this invariant.
(Previously: The specification preserved the invariant that scopes reduce access and never manufacture access, but no executable target-aware proving capability was required.)

#### Scenario: Base permission plus matching scope allows the target-aware capability

- GIVEN a principal has an otherwise valid allow path for `workspace:resource:read`
- AND the active workspace has a persisted scope whose allowed target set includes the requested `targetResourceId`
- WHEN the principal requests the new resource-preview proving capability
- THEN the platform MUST allow access
- AND the scope MUST operate only as a reduction over the already-valid base permission

#### Scenario: Base permission plus non-matching target is denied by scope reduction

- GIVEN a principal has an otherwise valid allow path for `workspace:resource:read`
- AND the active workspace has a persisted scope whose allowed target set does not include the requested `targetResourceId`
- WHEN the principal requests the new resource-preview proving capability
- THEN the platform MUST deny access
- AND the denial MUST be caused by scope reduction rather than by missing base permission

#### Scenario: Missing base permission plus any scope remains denied

- GIVEN a principal lacks any otherwise valid allow path for `workspace:resource:read`
- AND a persisted scope exists for the principal in the active workspace
- WHEN the principal requests the new resource-preview proving capability
- THEN the platform MUST deny access
- AND the scope MUST NOT manufacture the missing permission

### Requirement: Effective Permission Resolution Flow

The system MUST define deterministic effective permission resolution.

At minimum, effective resolution MUST consider principal identity, resource context, active workspace when applicable, membership, assigned roles, explicit role permissions, direct grants, denials, scopes, and applicable deferred-policy seams.
The platform MUST evaluate explicit denial before final allow.
The absence of an explicit allow path MUST result in denial.
Equivalent requests against equivalent authoritative state MUST resolve to the same result.
For `backend-scopes-execution`, the new target-aware proving capability MUST resolve the explicit base permission allow path before applying scope reduction to the requested `targetResourceId`.
Phase one MUST implement the subset of this flow required by the proving slice.
(Previously: The effective-resolution flow had to consider scopes semantically, but no executable requirement forced target-aware scope reduction after base allow-path resolution.)

#### Scenario: Effective permission resolves before scope reduction on the target-aware capability

- GIVEN an authenticated principal requests the new resource-preview proving capability
- AND the request identifies the active workspace in the supported phase-one form
- WHEN authorization is evaluated
- THEN the platform MUST resolve whether an explicit allow path exists for `workspace:resource:read` before applying scope reduction
- AND a missing allow path MUST result in denial even if persisted scope data exists

### Requirement: PostgreSQL Verification for the Target-Aware Scope Proving Capability

The system MUST verify the new target-aware resource-preview proving capability against real PostgreSQL as part of this scope-execution change.

That verification MUST execute the same protected slice against PostgreSQL-backed authoritative membership, role, grant, permission, and scope state rather than relying only on H2 PostgreSQL compatibility mode.
The verification MUST cover at least one authorized request path where base permission exists and the requested `targetResourceId` matches the persisted scope.
The verification MUST cover at least one denied request path where base permission exists and the requested `targetResourceId` does not match the persisted scope.
The verification MUST cover at least one denied request path where the principal lacks the base permission even if scope state exists.
This requirement MUST NOT be interpreted as a mandate to add a generic scope engine, wildcard support, inheritance, multi-context scopes, admin CRUD, quotas, billing or entitlement combinations, or broad policy redesign.

#### Scenario: PostgreSQL verifies base permission plus matching scope allow

- GIVEN the target-aware proving-slice schema is applied on a real PostgreSQL runtime
- AND an authenticated principal has an otherwise valid allow path for `workspace:resource:read`
- AND the active workspace has persisted scope state whose allowed target set includes the requested `targetResourceId`
- WHEN the principal requests the new resource-preview proving capability
- THEN the platform MUST allow access
- AND the result MUST prove scope reduction executes correctly against PostgreSQL-backed authoritative state

#### Scenario: PostgreSQL verifies base permission plus non-matching target deny

- GIVEN the target-aware proving-slice schema is applied on a real PostgreSQL runtime
- AND an authenticated principal has an otherwise valid allow path for `workspace:resource:read`
- AND the active workspace has persisted scope state whose allowed target set does not include the requested `targetResourceId`
- WHEN the principal requests the new resource-preview proving capability
- THEN the platform MUST deny access
- AND the denial MUST prove scope reduction blocks the disallowed target against PostgreSQL-backed authoritative state

#### Scenario: PostgreSQL verifies missing base permission plus any scope deny

- GIVEN the target-aware proving-slice schema is applied on a real PostgreSQL runtime
- AND an authenticated principal lacks any otherwise valid allow path for `workspace:resource:read`
- AND persisted scope state exists for that principal in the active workspace
- WHEN the principal requests the new resource-preview proving capability
- THEN the platform MUST deny access
- AND the denial MUST prove scope state does not manufacture access against PostgreSQL-backed authoritative state
