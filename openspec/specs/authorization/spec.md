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
A scope MUST NEVER create or expand permissions not already available through roles, direct grants,
or future policy evaluation.
Phase one MAY defer generalized scope authoring and management, but authorization evaluation
semantics MUST reserve this invariant.

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

At minimum, effective resolution MUST consider principal identity, resource context, active
workspace when applicable, membership, assigned roles, explicit role permissions, direct grants,
denials, scopes, and applicable deferred-policy seams.
The platform MUST evaluate explicit denial before final allow.
The absence of an explicit allow path MUST result in denial.
Equivalent requests against equivalent authoritative state MUST resolve to the same result.
Phase one MUST implement the subset of this flow required by the proving slice.

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
Feature entitlements MUST answer whether a tenant, workspace, or product context has the feature
available.
Authorization evaluation MAY require both entitlement and permission success for a capability that
is feature-gated.
The system MUST NOT treat feature entitlement as a substitute for principal permission.
Phase one MAY defer full entitlement management, but the separation MUST be preserved in platform
contracts.

#### Scenario: Entitlement without permission is insufficient

- GIVEN a workspace is entitled to a feature
- AND a principal lacks the required permission for that feature's protected action
- WHEN the principal requests the capability
- THEN the platform MUST deny access
- AND entitlement alone MUST NOT authorize the action

#### Scenario: Permission without entitlement can still be blocked

- GIVEN a principal has the required permission for a feature-gated capability
- AND the relevant workspace or tenant lacks the feature entitlement
- WHEN the capability is requested
- THEN the platform MUST deny or suppress the capability according to feature-gating rules
- AND the permission alone MUST NOT guarantee availability

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
- WHEN the change scope is evaluated
- THEN the addition MUST be treated as out of scope for `backend-authorization-breadth`
- AND it MUST be deferred to a later change

#### Scenario: Separate deny-rule subsystem remains deferred

- GIVEN the current direct-grant model already supports `DENY` effect
- WHEN scope for `backend-authorization-breadth` is defined
- THEN the specification MUST require direct deny behavior through persisted direct grants
- AND it MUST NOT require a separate deny-rule model or store in this change
