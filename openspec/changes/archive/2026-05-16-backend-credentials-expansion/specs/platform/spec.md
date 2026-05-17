# Delta for Platform

## MODIFIED Requirements

### Requirement: Stateless Scaling, Caching, and Invalidation Principles

The system MUST support stateless scaling principles for platform request processing.

Authorization and identity evaluation for API requests MUST be deterministic from explicit request
input and current authoritative platform state.
Caches MAY be used to improve performance.
Caches MUST reduce lookup cost without changing authorization semantics.
Cached authorization-related data MUST be invalidated or refreshed when underlying authoritative
state changes in a way that could affect effective permissions, scopes, grants, entitlements,
workspace membership, or credential validity.
For the implemented service-account bearer path, credential revocation state MUST be treated as
authoritative state for protected-request evaluation.
A technically valid presented service-account credential MUST NOT continue to authorize access when
current authoritative credential state revokes it.
Caches MUST NOT expand permissions beyond what authoritative state allows.
Phase one MAY use minimal or no cache implementation, but the platform seams MUST permit later safe
caching and invalidation.

(Previously: The requirement established invalidation principles for authorization-related state
changes, including credential validity, but did not yet require executable revocation enforcement on
a real non-user credential path.)

#### Scenario: Revoked service-account credential cannot retain cached access

- GIVEN a service-account credential was previously accepted for
  `/api/authorization/workspace-access/current`
- AND authoritative backend credential state later marks that credential as revoked
- WHEN the platform evaluates a later request with that same credential
- THEN any platform cache MUST NOT cause broader access than the revoked state allows
- AND the request outcome MUST converge to denial for the protected slice

### Requirement: Deterministic API Protection Principles

The system MUST enforce deny-by-default, explicit-over-implicit, and deterministic API protection
behavior.

Protected API behavior MUST require successful authentication, applicable context resolution, and
explicit authorization success before access is granted.
The absence of a required permission, grant, membership, or applicable rule MUST result in denial.
Explicit denial MUST override any allow path.
The system MUST NOT infer access from role names, token presence, or unspecified defaults.
Equivalent requests against equivalent state MUST produce equivalent authorization outcomes.
For the existing `/api/authorization/workspace-access/current` proving slice, the same protection
principles MUST apply to authenticated USER and authenticated SERVICE_ACCOUNT principals.
This change MUST prove end-to-end behavior for that slice with service-account allow,
authorization-controlled deny, and revoked-credential deny outcomes.
The proving slice MUST NOT broaden into new endpoints, broad credential-management surfaces, or
non-bearer authentication transports.

(Previously: The requirement defined deterministic protected-API behavior generically, without yet
requiring end-to-end proof for authenticated SERVICE_ACCOUNT principals on the existing proving
slice.)

#### Scenario: Service account is allowed on the current workspace-access slice

- GIVEN a persisted service account authenticates successfully through the existing bearer path
- AND the active workspace request is valid
- AND workspace membership and authorization facts explicitly allow access to
  `/api/authorization/workspace-access/current`
- WHEN the protected request is evaluated
- THEN the platform MUST allow the request
- AND the protected slice MUST return the allowed result for that service account

#### Scenario: Service account is denied by authorization on the current workspace-access slice

- GIVEN a persisted service account authenticates successfully through the existing bearer path
- AND the active workspace request is valid
- AND current workspace membership, permission, grant, or denial facts do not produce an explicit
  allow for `/api/authorization/workspace-access/current`
- WHEN the protected request is evaluated
- THEN the platform MUST deny the request
- AND the denial MUST be caused by authorization state rather than by credential-type mismatch

#### Scenario: Revoked service-account credential is denied on the current workspace-access slice

- GIVEN a persisted service account presents a bearer credential that would otherwise authenticate
  successfully
- AND authoritative backend credential state marks that credential as revoked
- WHEN the request targets `/api/authorization/workspace-access/current`
- THEN the platform MUST deny the request before protected access is granted
- AND the protected slice MUST NOT return an allowed result

#### Scenario: Rotation and broad credential management remain deferred

- GIVEN a requested capability requires credential rotation workflows, credential families, issuance
  consoles, or broad management APIs
- WHEN the proving-slice scope for this change is evaluated
- THEN that capability MUST be treated as deferred
- AND the current slice MUST proceed without broadening beyond service-account bearer authentication
  and revocation enforcement
