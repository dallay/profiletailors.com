# Delta for authorization

## ADDED Requirements

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
- WHEN the change scope is evaluated
- THEN the addition MUST be treated as out of scope for `backend-authorization-breadth`
- AND it MUST be deferred to a later change

#### Scenario: Separate deny-rule subsystem remains deferred

- GIVEN the current direct-grant model already supports `DENY` effect
- WHEN scope for `backend-authorization-breadth` is defined
- THEN the specification MUST require direct deny behavior through persisted direct grants
- AND it MUST NOT require a separate deny-rule model or store in this change

## MODIFIED Requirements

### Requirement: Minimal Proving Authorization Slice

The system MUST keep breadth expansion of the current authorization proof limited to the existing
protected proving slice at `/api/authorization/workspace-access/current`.

For this breadth change, the proving capability MUST continue to allow an authenticated USER
principal with an active workspace membership and the required explicit workspace-scoped permission
to retrieve the current workspace access summary for the active workspace.
For this breadth change, the proving slice MUST also execute persisted workspace-scoped direct
grants for the same required permission, including direct `ALLOW`, direct `DENY` override, and
expired-grant exclusion.
This breadth scope MUST remain sufficient to validate principal materialization, active workspace
resolution, membership lookup, role-based permission composition, persisted direct-grant evaluation,
deny-by-default behavior, protected query dispatch, and the previously required runtime audit-ready
proof and PostgreSQL-backed verification.
This breadth scope MUST NOT introduce new protected endpoints, new scope semantics,
entitlement-gated execution, or grant-management workflows.
(Previously: The proving slice was limited to role-based permission composition and hardening of the
existing endpoint without phase-2 breadth expansion.)

#### Scenario: Authorized principal retrieves workspace access summary through direct allow within breadth scope

- GIVEN an authenticated USER principal requests `/api/authorization/workspace-access/current`
- AND the request identifies the active workspace in the supported phase-one form
- AND the principal has an active membership in that workspace
- AND the principal lacks the required permission through assigned roles
- AND the principal has a persisted direct grant with `ALLOW` effect for the required permission in
  that workspace
- WHEN the protected capability is executed during this breadth scope
- THEN the platform MUST return the workspace access summary for that principal in that workspace
- AND the behavior MUST remain within the existing proving slice rather than expanding to additional
  protected capabilities

#### Scenario: Role-only allow path remains valid within breadth scope

- GIVEN an authenticated USER principal requests `/api/authorization/workspace-access/current`
- AND the request identifies the active workspace in the supported phase-one form
- AND the principal has an active membership in that workspace
- AND the principal has the required explicit workspace-scoped permission through assigned roles
- AND no applicable persisted direct deny blocks that permission
- WHEN the protected capability is executed during this breadth scope
- THEN the platform MUST allow access
- AND the existing role-based proving path MUST remain valid

### Requirement: PostgreSQL Verification for Workspace Access Current Slice

The system MUST verify the existing `/api/authorization/workspace-access/current` slice against real
PostgreSQL as part of this direct-grant breadth expansion.

That verification MUST execute the same protected slice against PostgreSQL-backed authoritative
grant, membership, role, and permission state rather than relying only on H2 PostgreSQL
compatibility mode.
The verification MUST cover at least one authorized request path produced by a persisted direct
allow grant, at least one denied request path produced by a persisted direct deny override, and at
least one denied request path where an expired persisted direct grant is ignored.
The verification MUST continue to prove compatibility of the current Liquibase execution, R2DBC
access, and SQL assumptions used by that slice.
This requirement MUST NOT be interpreted as a mandate to broaden verification into scopes,
entitlements, admin workflows, or unrelated protected endpoints.
(Previously: PostgreSQL verification covered the existing slice with at least one authorized and one
denied path, without requiring persisted direct-grant execution.)

#### Scenario: PostgreSQL verifies direct allow on the existing slice

- GIVEN the proving-slice schema is applied on a real PostgreSQL runtime
- AND an authenticated USER principal has an active workspace membership
- AND the principal lacks the required permission through assigned roles
- AND the principal has a persisted direct grant with `ALLOW` effect for the required permission in
  the active workspace
- WHEN the principal requests `/api/authorization/workspace-access/current`
- THEN the platform MUST return the workspace access summary successfully
- AND the result MUST prove persisted direct-grant execution works against PostgreSQL-backed
  authoritative state

#### Scenario: PostgreSQL verifies direct deny override on the existing slice

- GIVEN the proving-slice schema is applied on a real PostgreSQL runtime
- AND an authenticated USER principal has an active workspace membership
- AND the principal has the required explicit workspace-scoped permission through assigned roles
- AND the principal has a persisted direct grant with `DENY` effect for that same permission in the
  active workspace
- WHEN the principal requests `/api/authorization/workspace-access/current`
- THEN the platform MUST deny access
- AND the denial MUST prove persisted direct deny override works against PostgreSQL-backed
  authoritative state

#### Scenario: PostgreSQL verifies expired grant exclusion on the existing slice

- GIVEN the proving-slice schema is applied on a real PostgreSQL runtime
- AND an authenticated USER principal has an active workspace membership
- AND the principal lacks the required permission through assigned roles
- AND the principal has a persisted direct grant with `ALLOW` effect for the required permission in
  the active workspace
- AND that direct grant is expired at evaluation time
- WHEN the principal requests `/api/authorization/workspace-access/current`
- THEN the platform MUST deny access
- AND the denial MUST prove expired persisted direct grants are ignored against PostgreSQL-backed
  authoritative state
