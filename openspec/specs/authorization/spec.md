# Authorization Specification

## Purpose

Define authorization semantics for the reusable IAM platform. This specification establishes
permission format and explicitness rules, role and grant models, scope semantics, RBAC-to-ABAC
evolution, effective permission resolution behavior, feature entitlement separation, and the minimal
proving authorization slice required in phase one.

## Requirements

### Requirement: Explicit Permission Format and No Implicit Inheritance

The system MUST express permissions in the format `<domain>:<resource>:<action>`.

Permission identifiers MUST be explicit, stable, and feature-area-owned.
The platform MUST NOT infer additional permissions from naming conventions, prefix similarity, or
undeclared hierarchy.
Permission inheritance MUST be explicit if introduced in a later change.
Phase one MUST demonstrate at least one workspace-scoped permission in this format for the proving
slice.

#### Scenario: Permission is evaluated by explicit identifier

- GIVEN a protected capability requires authorization
- WHEN the capability declares its required permission
- THEN the platform MUST evaluate an explicit permission identifier in the format
  `<domain>:<resource>:<action>`
- AND it MUST NOT expand that requirement through implicit hierarchy

#### Scenario: Prefix similarity does not grant extra access

- GIVEN a principal has one explicit permission identifier
- WHEN another capability requires a different permission sharing the same domain or resource prefix
- THEN the platform MUST treat them as distinct permissions
- AND access MUST be denied unless the required permission is also granted explicitly

### Requirement: Role Categories and Composition

The system MUST model roles as compositions of permissions.

The platform MUST support role categories sufficient to distinguish SYSTEM roles, WORKSPACE roles,
and CUSTOM roles.
A role MUST grant access only through its explicitly associated permissions.
Role names or categories MUST NOT by themselves imply hidden permissions.
Phase one MUST support the role semantics needed for workspace-scoped authorization in the proving
slice.
Support for the full role-category management lifecycle MAY be deferred beyond phase one.

#### Scenario: Workspace role grants explicit permissions only

- GIVEN a workspace membership has one or more assigned roles
- WHEN authorization is evaluated
- THEN the platform MUST derive allowed access only from the explicit permissions attached to those
  roles
- AND it MUST NOT infer broader access from the role category or display name alone

#### Scenario: Custom role remains compatible with the core model

- GIVEN the platform later introduces a custom role for a workspace
- WHEN that role is evaluated for authorization
- THEN it MUST participate through the same permission-composition model as system-defined roles
- AND the core authorization contract MUST NOT need redesign

### Requirement: Direct Permission Grants and Denials

The system MUST support direct permission grants as a platform concept distinct from role
composition.

A direct permission grant MUST support ALLOW or DENY effect.
A direct permission grant MUST support an optional expiration.
A direct permission grant MUST support optional conditions as a platform concept.
Explicit DENY MUST override ALLOW when both apply to the same principal, permission, and effective
context.
Phase one MAY defer broad direct-grant management flows, but the model and evaluation path MUST
preserve direct grants as a first-class platform concept.

#### Scenario: Direct deny overrides role-based allow

- GIVEN a principal has a role that allows a required permission
- AND the principal also has a direct permission grant with DENY effect for that same permission in
  the relevant context
- WHEN authorization is evaluated
- THEN the platform MUST deny access
- AND the denial MUST override the role-based allow

#### Scenario: Expired direct grant no longer applies

- GIVEN a principal has a direct permission grant with an expiration
- WHEN authorization is evaluated after that expiration
- THEN the platform MUST treat the expired grant as inactive
- AND authorization MUST be determined without that expired grant

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

#### Scenario: Scope narrows an otherwise allowed action

- GIVEN a principal would otherwise be allowed a protected action
- AND an applicable scope narrows the set of allowed targets or operations
- WHEN authorization is evaluated
- THEN the scope MUST be able to reduce access
- AND the resulting access MUST be no broader than the pre-scope permission set

#### Scenario: Scope cannot manufacture access

- GIVEN a principal lacks any allow path for a required permission
- WHEN a scope is evaluated
- THEN the scope MUST NOT grant the missing permission
- AND access MUST remain denied

### Requirement: Policy Evolution Path from RBAC to RBAC plus ABAC

The platform MUST support an evolution path from RBAC to RBAC plus ABAC.

Phase one MUST provide deterministic RBAC-based authorization for the proving slice.
The platform architecture MUST preserve room for future ABAC conditions without invalidating the
core permission, role, and grant model.
Future ABAC introduction MUST augment explicit authorization evaluation and MUST NOT replace
deny-by-default or explicit-over-implicit principles.

#### Scenario: Phase one uses RBAC deterministically

- GIVEN the proving slice is implemented in phase one
- WHEN authorization is evaluated for that slice
- THEN the decision MUST be explainable through explicit RBAC facts in current platform state
- AND ABAC breadth is not required for phase-one completeness

#### Scenario: Future ABAC can augment the model

- GIVEN a later feature needs attribute-aware authorization
- WHEN the platform extends policy evaluation
- THEN the platform MUST be able to add ABAC conditions without redefining permission format, role
  composition, or denial precedence
- AND existing RBAC-based permissions MUST remain valid

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

#### Scenario: Effective permission is granted through membership and roles

- GIVEN an authenticated principal has an active workspace membership
- AND that membership has roles containing the required explicit permission
- AND no applicable denial blocks that permission
- WHEN effective authorization is evaluated for the protected capability
- THEN the platform MUST allow the request
- AND the decision MUST be attributable to the effective permission resolution flow

#### Scenario: Missing explicit allow path results in denial

- GIVEN an authenticated principal is evaluated for a protected capability
- AND no applicable role permission or direct allow grant provides the required permission
- WHEN authorization is resolved
- THEN the platform MUST deny the request
- AND it MUST do so even if the principal is otherwise authenticated and active in a workspace

### Requirement: Feature Entitlements Are Separate from Permissions

The system MUST model feature entitlements separately from permissions.

Permissions MUST answer whether a principal may perform an action.
Feature entitlements MUST answer whether the active workspace has the feature available for the protected capability being evaluated.
For this change, authorization evaluation for `/api/authorization/workspace-access/current` MUST require both workspace entitlement success and principal permission success.
The system MUST NOT treat feature entitlement as a substitute for principal permission.
The system MUST NOT treat principal permission as a substitute for feature entitlement when the capability is feature-gated.
For this change, the executable entitlement proof MUST remain limited to one persisted workspace-scoped feature key on the existing proving slice.

#### Scenario: Entitled and authorized request is allowed

- GIVEN the active workspace is entitled to the proving-slice feature
- AND the principal has the required explicit permission for `/api/authorization/workspace-access/current`
- WHEN the principal requests the capability
- THEN the platform MUST allow access
- AND the entitlement check MUST remain distinct from the permission check

#### Scenario: Authorized but not entitled request is denied

- GIVEN the principal has the required explicit permission for `/api/authorization/workspace-access/current`
- AND the active workspace is not entitled to the proving-slice feature
- WHEN the principal requests the capability
- THEN the platform MUST deny access
- AND permission success alone MUST NOT guarantee feature availability

#### Scenario: Entitled but unauthorized request is denied

- GIVEN the active workspace is entitled to the proving-slice feature
- AND the principal lacks the required explicit permission for `/api/authorization/workspace-access/current`
- WHEN the principal requests the capability
- THEN the platform MUST deny access
- AND entitlement success alone MUST NOT authorize the action

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

### Requirement: Minimal Proving Authorization Slice

The system MUST keep breadth expansion of the current authorization proof limited to the existing protected proving slice at `/api/authorization/workspace-access/current`.

For this breadth change, the proving capability MUST continue to allow an authenticated USER principal with an active workspace membership and the required explicit workspace-scoped permission to retrieve the current workspace access summary for the active workspace.
For this breadth change, the proving slice MUST also execute persisted workspace-scoped direct grants for the same required permission, including direct `ALLOW`, direct `DENY` override, and expired-grant exclusion.
For this breadth change, the proving slice MUST also execute one persisted workspace-scoped feature entitlement gate for that capability.
For this breadth change, access to `/api/authorization/workspace-access/current` MUST require both an effective allow path for the required permission and an enabled entitlement for the active workspace.
This breadth scope MUST remain sufficient to validate principal materialization, active workspace resolution, membership lookup, role-based permission composition, persisted direct-grant evaluation, workspace-scoped entitlement evaluation, deny-by-default behavior, protected query dispatch, and the previously required runtime audit-ready proof and PostgreSQL-backed verification.
This breadth scope MUST NOT introduce new protected endpoints, package or billing modeling, entitlement CRUD or admin workflows, multi-context entitlements, or quota and usage semantics.

#### Scenario: Authorized and entitled principal retrieves workspace access summary within breadth scope

- GIVEN an authenticated USER principal requests `/api/authorization/workspace-access/current`
- AND the request identifies the active workspace in the supported phase-one form
- AND the principal has an active membership in that workspace
- AND the principal has an effective allow path for the required explicit workspace-scoped permission
- AND the active workspace is entitled to the proving-slice feature
- WHEN the protected capability is executed during this breadth scope
- THEN the platform MUST return the workspace access summary for that principal in that workspace
- AND the behavior MUST remain within the existing proving slice rather than expanding to additional protected capabilities

#### Scenario: Authorized principal without entitlement is denied within breadth scope

- GIVEN an authenticated USER principal requests `/api/authorization/workspace-access/current`
- AND the request identifies the active workspace in the supported phase-one form
- AND the principal has an active membership in that workspace
- AND the principal has an effective allow path for the required explicit workspace-scoped permission
- AND the active workspace is not entitled to the proving-slice feature
- WHEN the protected capability is executed during this breadth scope
- THEN the platform MUST deny access
- AND the denial MUST be caused by missing entitlement rather than by missing permission

### Requirement: PostgreSQL Verification for Workspace Access Current Slice

The system MUST verify the existing `/api/authorization/workspace-access/current` slice against real PostgreSQL as part of this entitlement-gating breadth change.

That verification MUST execute the same protected slice against PostgreSQL-backed authoritative grant, membership, role, permission, and workspace entitlement state rather than relying only on H2 PostgreSQL compatibility mode.
The verification MUST cover at least one authorized request path where the active workspace is entitled and the principal is authorized.
The verification MUST cover at least one denied request path where the active workspace is not entitled but the principal is authorized.
The verification MUST cover at least one denied request path where the active workspace is entitled but the principal is unauthorized.
The verification MUST continue to prove compatibility of the current Liquibase execution, R2DBC access, and SQL assumptions used by that slice.
This requirement MUST NOT be interpreted as a mandate to broaden verification into package modeling, billing integration, entitlement admin workflows, multi-context breadth, quotas, or unrelated protected endpoints.

#### Scenario: PostgreSQL verifies entitled and authorized allow on the existing slice

- GIVEN the proving-slice schema is applied on a real PostgreSQL runtime
- AND an authenticated USER principal has an active workspace membership
- AND the principal has an effective allow path for the required explicit workspace-scoped permission
- AND the active workspace is entitled to the proving-slice feature
- WHEN the principal requests `/api/authorization/workspace-access/current`
- THEN the platform MUST return the workspace access summary successfully
- AND the result MUST prove combined entitlement and permission evaluation works against PostgreSQL-backed authoritative state

#### Scenario: PostgreSQL verifies authorized but non-entitled deny on the existing slice

- GIVEN the proving-slice schema is applied on a real PostgreSQL runtime
- AND an authenticated USER principal has an active workspace membership
- AND the principal has an effective allow path for the required explicit workspace-scoped permission
- AND the active workspace is not entitled to the proving-slice feature
- WHEN the principal requests `/api/authorization/workspace-access/current`
- THEN the platform MUST deny access
- AND the denial MUST prove missing workspace entitlement blocks the capability against PostgreSQL-backed authoritative state

#### Scenario: PostgreSQL verifies entitled but unauthorized deny on the existing slice

- GIVEN the proving-slice schema is applied on a real PostgreSQL runtime
- AND an authenticated USER principal has an active workspace membership
- AND the principal lacks an effective allow path for the required explicit workspace-scoped permission
- AND the active workspace is entitled to the proving-slice feature
- WHEN the principal requests `/api/authorization/workspace-access/current`
- THEN the platform MUST deny access
- AND the denial MUST prove missing principal permission blocks the capability against PostgreSQL-backed authoritative state

### Requirement: Hardening Change Boundary for Workspace Access Current Slice

The system MUST treat hardening of the current workspace-access proof as a proving-slice hardening
change only.

That hardening scope MUST include only the requirements necessary to produce runtime audit-ready
allow/deny proof and PostgreSQL-backed verification for
`/api/authorization/workspace-access/current`.
That hardening scope MUST NOT add full governance workflows, audit persistence, compliance
reporting, policy administration, structural architecture guardrails, or additional protected
endpoint coverage.
Capabilities outside this narrow slice MAY be addressed only in later changes.

#### Scenario: Out-of-scope expansion is rejected for this change

- GIVEN a proposed addition does not directly support runtime audit-ready proof or PostgreSQL-backed
  verification for `/api/authorization/workspace-access/current`
- WHEN the change scope is evaluated
- THEN the addition MUST be treated as out of scope for that hardening scope
- AND it MUST be deferred to a later change rather than included in the current requirements

#### Scenario: Deferred items remain explicitly deferred

- GIVEN the hardening scope is specified for implementation planning
- WHEN deferred work is enumerated
- THEN full governance build-out, broad PostgreSQL migration, phase-2 feature expansion, and
  structural architecture guardrails MUST remain explicitly deferred
- AND the specification MUST NOT require them for completion of that hardening scope

### Requirement: Persisted Workspace-Scoped Direct Grants for the Existing Proving Slice

The system MUST support persisted workspace-scoped direct grants as authoritative authorization
input for the existing `/api/authorization/workspace-access/current` proving slice.

A persisted direct grant MUST bind a principal, active workspace context, explicit permission
identifier, and effect of `ALLOW` or `DENY`.
A persisted direct grant MUST support an optional expiration.
For this change, authorization evaluation for `/api/authorization/workspace-access/current` MUST use
applicable non-expired persisted direct grants together with the existing role-based allow path.
A persisted direct grant that is expired at evaluation time MUST be ignored.
When both a direct `DENY` grant and an allow path apply to the same principal, workspace, and
required permission for this slice, the system MUST deny access.
This change MUST remain limited to workspace-scoped direct grants for the existing proving slice and
MUST NOT require broader direct-grant execution on other authorization surfaces.

#### Scenario: Direct allow grants access on the existing proving slice

- GIVEN an authenticated USER principal requests `/api/authorization/workspace-access/current`
- AND the request identifies an active workspace in the supported phase-one form
- AND the principal has an active membership in that workspace
- AND the principal lacks the required permission through assigned roles
- AND the principal has a persisted direct grant with `ALLOW` effect for the required permission in
  that workspace
- WHEN authorization is evaluated for the existing proving slice
- THEN the platform MUST allow access
- AND the workspace access summary MUST be returned for that workspace

#### Scenario: Direct deny overrides role-based allow on the existing proving slice

- GIVEN an authenticated USER principal requests `/api/authorization/workspace-access/current`
- AND the request identifies an active workspace in the supported phase-one form
- AND the principal has an active membership in that workspace
- AND the principal has the required permission through assigned roles
- AND the principal has a persisted direct grant with `DENY` effect for that same permission in that
  workspace
- WHEN authorization is evaluated for the existing proving slice
- THEN the platform MUST deny access
- AND the direct deny MUST override the role-based allow

#### Scenario: Expired persisted direct grant is ignored on the existing proving slice

- GIVEN an authenticated USER principal requests `/api/authorization/workspace-access/current`
- AND the request identifies an active workspace in the supported phase-one form
- AND the principal has an active membership in that workspace
- AND the principal lacks the required permission through assigned roles
- AND the principal has a persisted direct grant with `ALLOW` effect for the required permission in
  that workspace
- AND that direct grant is expired at evaluation time
- WHEN authorization is evaluated for the existing proving slice
- THEN the platform MUST treat the expired direct grant as inactive
- AND access MUST be denied because no active allow path remains

### Requirement: Direct-Grant Breadth Boundary for the Existing Proving Slice

The system MUST treat `backend-authorization-breadth` as a narrow breadth expansion for direct
grants only.

This change MUST include only the behaviors required to make persisted workspace-scoped direct
grants executable on `/api/authorization/workspace-access/current`, including direct allow, direct
deny override, and expired-grant exclusion.
This change MUST NOT require scope evaluation, entitlement enforcement, feature-gated execution,
direct-grant administration workflows, generalized policy APIs, or broader authorization-surface
expansion.
A separate deny-rule subsystem beyond direct grants with `DENY` effect MUST remain deferred.

#### Scenario: Deferred breadth expansion is rejected

- GIVEN a proposed addition does not directly support persisted workspace-scoped direct-grant
  evaluation for `/api/authorization/workspace-access/current`
- WHEN the scope for `backend-authorization-breadth` is evaluated
- THEN the addition MUST be treated as out of scope for `backend-authorization-breadth`
- AND it MUST be deferred to a later change

#### Scenario: Separate deny-rule subsystem remains deferred

- GIVEN the current direct-grant model already supports `DENY` effect
- WHEN scope for `backend-authorization-breadth` is defined
- THEN the specification MUST require direct deny behavior through persisted direct grants
- AND it MUST NOT require a separate deny-rule model or store in this change

### Requirement: Persisted Workspace-Scoped Feature Entitlement for the Existing Proving Slice

The system MUST support one persisted workspace-scoped feature entitlement key as authoritative feature-availability state for the existing `/api/authorization/workspace-access/current` proving slice.

For this change, the entitlement model MUST be sufficient to answer whether a specific workspace is enabled for that one proving-slice feature key.
The persisted entitlement state MUST be workspace-scoped.
The persisted entitlement state MUST distinguish enabled from not enabled for that key.
This change MUST NOT require package, billing, catalog, bundle, subscription, quota, usage, inheritance, fallback, or multi-context semantics.
This change MUST NOT require entitlement CRUD, assignment, administration, or operator APIs.

#### Scenario: Entitlement state exists for one workspace-scoped proving key

- GIVEN the existing proving slice requires feature entitlement evaluation
- WHEN the platform resolves entitlement state for `/api/authorization/workspace-access/current`
- THEN the platform MUST evaluate one persisted workspace-scoped feature key for the active workspace
- AND the result MUST be sufficient to determine whether that proving-slice feature is enabled for that workspace

#### Scenario: Missing broader commercial semantics remains deferred

- GIVEN a requested addition introduces package, billing, subscription, or quota semantics
- WHEN the scope for `backend-feature-entitlements` is evaluated
- THEN that addition MUST be treated as out of scope for this change
- AND the current change MUST proceed without requiring those broader semantics

### Requirement: Feature-Entitlement Change Boundary for the Existing Proving Slice

The system MUST treat `backend-feature-entitlements` as a narrow executable entitlement-gating change for the existing proving slice only.

This change MUST include only the behaviors required to persist one workspace-scoped proving feature key and execute entitlement gating on `/api/authorization/workspace-access/current`.
This change MUST NOT require new protected endpoints, package or billing modeling, entitlement CRUD or admin APIs, multi-context entitlement breadth, quota or usage semantics, or generalized commercial feature management.
The current change MUST explicitly defer broader entitlement breadth beyond the existing proving slice.

#### Scenario: New endpoint expansion is rejected

- GIVEN a proposed addition introduces a new protected endpoint to prove entitlement behavior
- WHEN the scope for `backend-feature-entitlements` is evaluated
- THEN that addition MUST be treated as out of scope for this change
- AND the proving work MUST remain on `/api/authorization/workspace-access/current`

#### Scenario: Broader entitlement platform breadth remains deferred

- GIVEN a requested addition introduces admin APIs, multi-context entitlements, or quota semantics
- WHEN the scope for `backend-feature-entitlements` is reviewed
- THEN those additions MUST be considered deferred
- AND the specification MUST NOT require them for completion of this change
