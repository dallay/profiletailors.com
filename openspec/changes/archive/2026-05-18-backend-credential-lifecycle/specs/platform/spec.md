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
authorization-controlled deny, revoked-or-inactive-credential deny, and completed-replacement
cutover outcomes.
For the supported API-key replacement capability, the platform MUST apply one explicit runtime rule:
after the replacement operation completes, the successor API key MUST be accepted and the
predecessor API key MUST be denied.
The completed replacement rule MUST NOT allow any overlap window where both predecessor and
successor are accepted on `/api/authorization/workspace-access/current`.
The proving slice MUST remain limited to `/api/authorization/workspace-access/current`.
The proving slice MUST NOT broaden into new endpoints, service-account rotation, dual-active
rollover windows, inventory or detail APIs, or generalized credential-family management.
The proving slice MUST NOT broaden into broad issuance/admin platform behavior beyond what is
minimally necessary to execute one API-key replacement path.

(Previously: This change proved API-key allow, authorization-controlled deny, and
revoked-or-inactive-credential deny outcomes, while rotation workflows, inventory surfaces, and
broad credential-management redesign remained deferred.)

#### Scenario: Old API key allows access before replacement

- GIVEN a persisted API-key credential authenticates successfully for
  `/api/authorization/workspace-access/current`
- AND the active workspace request is valid
- AND workspace membership and authorization facts explicitly allow access for the bound principal
- AND no completed replacement has made that credential a predecessor
- WHEN the protected request is evaluated
- THEN the platform MUST allow the request
- AND the protected slice MUST return the allowed result for that API-key principal

#### Scenario: New API key allows access after replacement

- GIVEN an existing active API-key credential has been replaced through the supported replacement
  capability
- AND the successor API-key credential now authenticates successfully for
  `/api/authorization/workspace-access/current`
- AND the active workspace request is valid
- AND workspace membership and authorization facts explicitly allow access for the bound principal
- WHEN the protected request is evaluated with the successor API key
- THEN the platform MUST allow the request
- AND the protected slice MUST return the allowed result for that API-key principal

#### Scenario: Old API key is denied after replacement

- GIVEN an existing active API-key credential has been replaced through the supported replacement
  capability
- AND the predecessor API key would otherwise match and verify successfully
- WHEN the request targets `/api/authorization/workspace-access/current` with that predecessor API
  key
- THEN the platform MUST deny the request before protected access is granted
- AND the protected slice MUST NOT return an allowed result

#### Scenario: Replacement proof stays on the existing proving endpoint

- GIVEN the replacement capability has completed for an API-key credential pair
- WHEN end-to-end proof for this change is defined
- THEN the proof MUST run on `/api/authorization/workspace-access/current`
- AND the change MUST NOT require new proving endpoints to express before-and-after behavior

#### Scenario: Broad credential lifecycle platform behavior remains deferred

- GIVEN a requested capability requires service-account rotation, dual-active rollover windows,
  inventory/list/detail APIs, or generalized credential-family management
- WHEN the proving-slice scope for this change is evaluated
- THEN that capability MUST be treated as deferred
- AND the current slice MUST proceed without broadening beyond one API-key replacement cutover path
