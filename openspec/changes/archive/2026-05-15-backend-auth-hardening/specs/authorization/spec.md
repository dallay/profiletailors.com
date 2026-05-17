# Delta for Authorization

## MODIFIED Requirements

### Requirement: Minimal Proving Authorization Slice

The system MUST keep the hardening change scoped to the existing protected proving slice at
`/api/authorization/workspace-access/current`.

For this change, the proving capability MUST continue to allow an authenticated USER principal with
an active workspace membership and the required explicit workspace-scoped permission to retrieve the
current workspace access summary for the active workspace.
The proving slice MUST remain sufficient to validate principal materialization, active workspace
resolution, membership lookup, role-based permission composition, deny-by-default behavior,
protected query dispatch, and the hardening requirements added for runtime audit-ready proof and
PostgreSQL-backed verification.
This change MUST NOT introduce new protected endpoints, new authorization capabilities, or phase-2
breadth expansion.
Broader workspace administration, custom grant management, full entitlement workflows, and phase-2
authorization expansion remain deferred.

(Previously: The system provided one minimal protected proving slice in phase one focused on
validating principal materialization, active workspace resolution, membership lookup, role-based
permission composition, deny-by-default behavior, and protected query dispatch, while broader
administration and entitlement workflows were deferred.)

#### Scenario: Authorized principal retrieves workspace access summary within hardening scope

- GIVEN an authenticated USER principal requests `/api/authorization/workspace-access/current`
- AND the request identifies the active workspace in the supported phase-one form
- AND the principal has an active membership in that workspace
- AND the principal has the required explicit workspace-scoped permission
- WHEN the protected capability is executed during this hardening change
- THEN the platform MUST return the workspace access summary for that principal in that workspace
- AND the behavior MUST remain within the existing proving slice rather than expanding to additional
  protected capabilities

#### Scenario: Missing required permission still blocks the existing proving slice

- GIVEN an authenticated USER principal requests `/api/authorization/workspace-access/current`
- AND the request identifies the active workspace in the supported phase-one form
- AND the principal is a member of that workspace
- AND the principal lacks the required explicit permission
- WHEN the protected capability is executed during this hardening change
- THEN the platform MUST deny access
- AND the protected summary MUST NOT be returned
- AND the denial MUST be treated as hardening of the existing slice rather than as a new
  authorization feature

## ADDED Requirements

### Requirement: PostgreSQL Verification for Workspace Access Current Slice

The system MUST verify the existing `/api/authorization/workspace-access/current` slice against real
PostgreSQL for this hardening change.

That verification MUST execute the same protected slice against PostgreSQL-backed schema and data
paths rather than relying only on H2 PostgreSQL compatibility mode.
The verification MUST cover at least one authorized request path and at least one denied request
path for the same slice.
The verification MUST prove compatibility of the current Liquibase execution, R2DBC access, and SQL
assumptions used by that slice.
This requirement MUST NOT be interpreted as a mandate to migrate the full backend test suite to
PostgreSQL in this change.

#### Scenario: Authorized slice succeeds on PostgreSQL

- GIVEN the proving-slice schema is applied on a real PostgreSQL runtime
- AND an authenticated USER principal has an active workspace membership with the required explicit
  workspace-scoped permission
- WHEN the principal requests `/api/authorization/workspace-access/current`
- THEN the platform MUST return the workspace access summary successfully
- AND the result MUST prove the slice works against PostgreSQL-backed Liquibase and R2DBC execution

#### Scenario: Denied slice is enforced on PostgreSQL

- GIVEN the proving-slice schema is applied on a real PostgreSQL runtime
- AND an authenticated USER principal is a member of the active workspace
- AND the principal lacks the required explicit workspace-scoped permission
- WHEN the principal requests `/api/authorization/workspace-access/current`
- THEN the platform MUST deny access
- AND the denial MUST be verified on PostgreSQL rather than inferred from H2 compatibility mode

### Requirement: Hardening Change Boundary for Workspace Access Current Slice

The system MUST treat `backend-auth-hardening` as a proving-slice hardening change only.

This change MUST include only the requirements necessary to produce runtime audit-ready allow/deny
proof and PostgreSQL-backed verification for `/api/authorization/workspace-access/current`.
This change MUST NOT add full governance workflows, audit persistence, compliance reporting, policy
administration, structural architecture guardrails, or additional protected endpoint coverage.
Capabilities outside this narrow slice MAY be addressed only in later changes.

#### Scenario: Out-of-scope expansion is rejected for this change

- GIVEN a proposed addition does not directly support runtime audit-ready proof or PostgreSQL-backed
  verification for `/api/authorization/workspace-access/current`
- WHEN the change scope is evaluated
- THEN the addition MUST be treated as out of scope for `backend-auth-hardening`
- AND it MUST be deferred to a later change rather than included in this spec set

#### Scenario: Deferred items remain explicitly deferred

- GIVEN the hardening change is specified for implementation planning
- WHEN deferred work is enumerated
- THEN full governance build-out, broad PostgreSQL migration, phase-2 feature expansion, and
  structural architecture guardrails MUST remain explicitly deferred
- AND the spec MUST NOT require them for completion of this change
