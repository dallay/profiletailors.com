# Tenancy Specification

## Purpose

Define workspace and tenancy semantics for the reusable IAM platform. This specification establishes
workspace lifecycle and ownership as platform concepts distinct from authorization roles, plus the
membership model and phase-one workspace rules required for deterministic workspace-scoped
authorization.

## Requirements

### Requirement: Workspace Lifecycle and Ownership Are Distinct from Roles

The system MUST model workspace lifecycle and workspace ownership separately from the authorization
role model.

Workspace ownership MUST represent who controls or is accountable for the workspace as a tenancy
concept.
Workspace ownership MUST NOT be reduced to a role alias.
Workspace lifecycle state transitions and ownership changes MUST remain tenancy concerns even when
authorization checks consult them.
Phase one MUST preserve this distinction in the model even if ownership behavior breadth is
minimally implemented.

#### Scenario: Ownership does not collapse into role semantics

- GIVEN a workspace has an owning principal or ownership authority
- WHEN authorization roles are also defined for that workspace
- THEN the platform MUST treat ownership as a tenancy concept distinct from role assignment
- AND the ownership model MUST NOT be represented solely as a special role name

#### Scenario: Ownership semantics survive role changes

- GIVEN a principal's workspace role assignments change
- WHEN the workspace ownership model is evaluated
- THEN ownership semantics MUST remain independently representable
- AND the platform MUST NOT assume ownership changed merely because roles changed

### Requirement: Workspace Membership Supports Multiple Roles per Principal

The system MUST model workspace membership as the relationship between a principal and a workspace.

A membership MUST allow more than one role to be assigned to the same principal in the same
workspace.
A principal MUST have an active membership before it can exercise workspace-scoped permissions in
that workspace.
Phase one MUST implement the membership semantics required for USER principals in the proving slice.
Support for additional principal types in memberships is platform-required and MAY be deferred
beyond the phase-one path.

#### Scenario: Membership carries multiple roles in one workspace

- GIVEN a principal belongs to a workspace
- AND the principal is assigned more than one role in that workspace
- WHEN effective permissions are evaluated
- THEN the platform MUST treat those roles as part of the same workspace membership context
- AND authorization MAY combine their explicit allowed permissions subject to denial rules

#### Scenario: Missing membership denies workspace access

- GIVEN an authenticated principal requests a workspace-scoped protected capability
- WHEN no active membership exists for that principal in the active workspace
- THEN the platform MUST deny access
- AND the protected use case MUST NOT run

### Requirement: Active Workspace Context in Tenancy

The system MUST provide workspace context facts needed by authorization evaluation.

For phase one, the platform MUST resolve a single active workspace for each protected
workspace-scoped request.
The active workspace MUST be explicit and MUST NOT be inferred from unrelated identity data alone.
The platform MUST expose the resolved workspace context to downstream application and authorization
behavior through repo-local seams.
Broader workspace discovery, default workspace preferences, and cross-workspace request
orchestration are deferred.

#### Scenario: Workspace context is exposed to downstream behavior

- GIVEN a protected request resolves an active workspace successfully
- WHEN downstream application and authorization logic execute
- THEN the platform MUST make that active workspace available through tenancy-aware seams
- AND downstream behavior MUST use the same resolved workspace context consistently

#### Scenario: Ambiguous workspace context is not allowed in phase one

- GIVEN a protected request could imply more than one workspace context
- WHEN phase-one workspace handling is applied
- THEN the platform MUST reject or constrain the request to one explicit active workspace
- AND it MUST NOT authorize against an ambiguous workspace target
