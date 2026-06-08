# Delta for authorization

## ADDED Requirements

### Requirement: Persisted Workspace-Scoped Feature Entitlement for the Existing Proving Slice

The system MUST support one persisted workspace-scoped feature entitlement key as authoritative
feature-availability state for the existing `/api/authorization/workspace-access/current` proving
slice.

For this change, the entitlement model MUST be sufficient to answer whether a specific workspace is
enabled for that one proving-slice feature key.
The persisted entitlement state MUST be workspace-scoped.
The persisted entitlement state MUST distinguish enabled from not enabled for that key.
This change MUST NOT require package, billing, catalog, bundle, subscription, quota, usage,
inheritance, fallback, or multi-context semantics.
This change MUST NOT require entitlement CRUD, assignment, administration, or operator APIs.

#### Scenario: Entitlement state exists for one workspace-scoped proving key

- GIVEN the existing proving slice requires feature entitlement evaluation
- WHEN the platform resolves entitlement state for `/api/authorization/workspace-access/current`
- THEN the platform MUST evaluate one persisted workspace-scoped feature key for the active
  workspace
- AND the result MUST be sufficient to determine whether that proving-slice feature is enabled for
  that workspace

#### Scenario: Missing broader commercial semantics remains deferred

- GIVEN a requested addition introduces package, billing, subscription, or quota semantics
- WHEN the scope for `backend-feature-entitlements` is evaluated
- THEN that addition MUST be treated as out of scope for this change
- AND the current change MUST proceed without requiring those broader semantics

### Requirement: Feature-Entitlement Change Boundary for the Existing Proving Slice

The system MUST treat `backend-feature-entitlements` as a narrow executable entitlement-gating
change for the existing proving slice only.

This change MUST include only the behaviors required to persist one workspace-scoped proving feature
key and execute entitlement gating on `/api/authorization/workspace-access/current`.
This change MUST NOT require new protected endpoints, package or billing modeling, entitlement CRUD
or admin APIs, multi-context entitlement breadth, quota or usage semantics, or generalized
commercial feature management.
The current change MUST explicitly defer broader entitlement breadth beyond the existing proving
slice.

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

## MODIFIED Requirements

### Requirement: Feature Entitlements Are Separate from Permissions

The system MUST model feature entitlements separately from permissions.

Permissions MUST answer whether a principal may perform an action.
Feature entitlements MUST answer whether the active workspace has the feature available for the
protected capability being evaluated.
For this change, authorization evaluation for `/api/authorization/workspace-access/current` MUST
require both workspace entitlement success and principal permission success.
The system MUST NOT treat feature entitlement as a substitute for principal permission.
The system MUST NOT treat principal permission as a substitute for feature entitlement when the
capability is feature-gated.
For this change, the executable entitlement proof MUST remain limited to one persisted
workspace-scoped feature key on the existing proving slice.
(Previously: Feature entitlements were modeled separately from permissions, and capability
evaluation MAY require both checks, but full entitlement management could remain deferred without
executable proving-slice enforcement.)

#### Scenario: Entitled and authorized request is allowed

- GIVEN the active workspace is entitled to the proving-slice feature
- AND the principal has the required explicit permission for
  `/api/authorization/workspace-access/current`
- WHEN the principal requests the capability
- THEN the platform MUST allow access
- AND the entitlement check MUST remain distinct from the permission check

#### Scenario: Authorized but not entitled request is denied

- GIVEN the principal has the required explicit permission for
  `/api/authorization/workspace-access/current`
- AND the active workspace is not entitled to the proving-slice feature
- WHEN the principal requests the capability
- THEN the platform MUST deny access
- AND permission success alone MUST NOT guarantee feature availability

#### Scenario: Entitled but unauthorized request is denied

- GIVEN the active workspace is entitled to the proving-slice feature
- AND the principal lacks the required explicit permission for
  `/api/authorization/workspace-access/current`
- WHEN the principal requests the capability
- THEN the platform MUST deny access
- AND entitlement success alone MUST NOT authorize the action

### Requirement: Minimal Proving Authorization Slice

The system MUST keep breadth expansion of the current authorization proof limited to the existing
protected proving slice at `/api/authorization/workspace-access/current`.

For this breadth change, the proving capability MUST continue to allow an authenticated USER
principal with an active workspace membership and the required explicit workspace-scoped permission
to retrieve the current workspace access summary for the active workspace.
For this breadth change, the proving slice MUST also execute persisted workspace-scoped direct
grants for the same required permission, including direct `ALLOW`, direct `DENY` override, and
expired-grant exclusion.
For this breadth change, the proving slice MUST also execute one persisted workspace-scoped feature
entitlement gate for that capability.
For this breadth change, access to `/api/authorization/workspace-access/current` MUST require both
an effective allow path for the required permission and an enabled entitlement for the active
workspace.
This breadth scope MUST remain sufficient to validate principal materialization, active workspace
resolution, membership lookup, role-based permission composition, persisted direct-grant evaluation,
workspace-scoped entitlement evaluation, deny-by-default behavior, protected query dispatch, and the
previously required runtime audit-ready proof and PostgreSQL-backed verification.
This breadth scope MUST NOT introduce new protected endpoints, package or billing modeling,
entitlement CRUD or admin workflows, multi-context entitlements, or quota and usage semantics.
(Previously: The proving slice executed persisted workspace-scoped direct grants on the existing
endpoint and explicitly excluded entitlement-gated execution.)

#### Scenario: Authorized and entitled principal retrieves workspace access summary within breadth scope

- GIVEN an authenticated USER principal requests `/api/authorization/workspace-access/current`
- AND the request identifies the active workspace in the supported phase-one form
- AND the principal has an active membership in that workspace
- AND the principal has an effective allow path for the required explicit workspace-scoped
  permission
- AND the active workspace is entitled to the proving-slice feature
- WHEN the protected capability is executed during this breadth scope
- THEN the platform MUST return the workspace access summary for that principal in that workspace
- AND the behavior MUST remain within the existing proving slice rather than expanding to additional
  protected capabilities

#### Scenario: Authorized principal without entitlement is denied within breadth scope

- GIVEN an authenticated USER principal requests `/api/authorization/workspace-access/current`
- AND the request identifies the active workspace in the supported phase-one form
- AND the principal has an active membership in that workspace
- AND the principal has an effective allow path for the required explicit workspace-scoped
  permission
- AND the active workspace is not entitled to the proving-slice feature
- WHEN the protected capability is executed during this breadth scope
- THEN the platform MUST deny access
- AND the denial MUST be caused by missing entitlement rather than by missing permission

### Requirement: PostgreSQL Verification for Workspace Access Current Slice

The system MUST verify the existing `/api/authorization/workspace-access/current` slice against real
PostgreSQL as part of this entitlement-gating breadth change.

That verification MUST execute the same protected slice against PostgreSQL-backed authoritative
grant, membership, role, permission, and workspace entitlement state rather than relying only on H2
PostgreSQL compatibility mode.
The verification MUST cover at least one authorized request path where the active workspace is
entitled and the principal is authorized.
The verification MUST cover at least one denied request path where the active workspace is not
entitled but the principal is authorized.
The verification MUST cover at least one denied request path where the active workspace is entitled
but the principal is unauthorized.
The verification MUST continue to prove compatibility of the current Liquibase execution, R2DBC
access, and SQL assumptions used by that slice.
This requirement MUST NOT be interpreted as a mandate to broaden verification into package modeling,
billing integration, entitlement admin workflows, multi-context breadth, quotas, or unrelated
protected endpoints.
(Previously: PostgreSQL verification covered direct-grant execution paths on the existing slice and
explicitly excluded entitlement-related verification.)

#### Scenario: PostgreSQL verifies entitled and authorized allow on the existing slice

- GIVEN the proving-slice schema is applied on a real PostgreSQL runtime
- AND an authenticated USER principal has an active workspace membership
- AND the principal has an effective allow path for the required explicit workspace-scoped
  permission
- AND the active workspace is entitled to the proving-slice feature
- WHEN the principal requests `/api/authorization/workspace-access/current`
- THEN the platform MUST return the workspace access summary successfully
- AND the result MUST prove combined entitlement and permission evaluation works against
  PostgreSQL-backed authoritative state

#### Scenario: PostgreSQL verifies authorized but non-entitled deny on the existing slice

- GIVEN the proving-slice schema is applied on a real PostgreSQL runtime
- AND an authenticated USER principal has an active workspace membership
- AND the principal has an effective allow path for the required explicit workspace-scoped
  permission
- AND the active workspace is not entitled to the proving-slice feature
- WHEN the principal requests `/api/authorization/workspace-access/current`
- THEN the platform MUST deny access
- AND the denial MUST prove missing workspace entitlement blocks the capability against
  PostgreSQL-backed authoritative state

#### Scenario: PostgreSQL verifies entitled but unauthorized deny on the existing slice

- GIVEN the proving-slice schema is applied on a real PostgreSQL runtime
- AND an authenticated USER principal has an active workspace membership
- AND the principal lacks an effective allow path for the required explicit workspace-scoped
  permission
- AND the active workspace is entitled to the proving-slice feature
- WHEN the principal requests `/api/authorization/workspace-access/current`
- THEN the platform MUST deny access
- AND the denial MUST prove missing principal permission blocks the capability against
  PostgreSQL-backed authoritative state
