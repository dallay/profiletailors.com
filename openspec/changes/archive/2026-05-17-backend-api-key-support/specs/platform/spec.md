# Delta for Platform

## MODIFIED Requirements

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
principles MUST apply to authenticated USER, authenticated SERVICE_ACCOUNT, and authenticated
API_KEY principals.
This change MUST prove end-to-end behavior for that slice with API-key allow,
authorization-controlled deny, and revoked-or-inactive-credential deny outcomes.
The proving slice MUST NOT broaden into new endpoints, issuance or admin APIs, rotation workflows,
inventory surfaces, or broad credential-management redesign.
(Previously: The proving slice requirements covered authenticated USER and authenticated
SERVICE_ACCOUNT principals only, and the breadth boundary deferred non-bearer authentication
transports.)

#### Scenario: API key is allowed on the current workspace-access slice

- GIVEN a persisted API-key credential authenticates successfully for
  `/api/authorization/workspace-access/current`
- AND the active workspace request is valid
- AND workspace membership and authorization facts explicitly allow access for the bound principal
- WHEN the protected request is evaluated
- THEN the platform MUST allow the request
- AND the protected slice MUST return the allowed result for that API-key principal

#### Scenario: API key is denied by authorization on the current workspace-access slice

- GIVEN a persisted API-key credential authenticates successfully for
  `/api/authorization/workspace-access/current`
- AND the active workspace request is valid
- AND current workspace membership, permission, grant, or denial facts do not produce an explicit
  allow for the bound principal
- WHEN the protected request is evaluated
- THEN the platform MUST deny the request
- AND the denial MUST be caused by authorization state rather than by credential-type mismatch

#### Scenario: Inactive or revoked API key is denied on the current workspace-access slice

- GIVEN a persisted API-key credential presents a secret that would otherwise authenticate
  successfully
- AND authoritative backend credential state marks that credential as inactive or revoked
- WHEN the request targets `/api/authorization/workspace-access/current`
- THEN the platform MUST deny the request before protected access is granted
- AND the protected slice MUST NOT return an allowed result
