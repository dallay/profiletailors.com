# Delta for Identity

## MODIFIED Requirements

### Requirement: Principal Taxonomy

The system MUST define the following principal taxonomy: USER, SERVICE_ACCOUNT, API_KEY, SYSTEM,
INTEGRATION, and AGENT.

Every authenticated actor recognized by the platform MUST be represented as one of these principal
types.
The principal taxonomy MUST be part of the stable platform model even if a given phase authenticates
only a subset of principal types.
Phase one MUST implement the USER principal path for the proving slice.
This change MUST additionally implement persisted `SERVICE_ACCOUNT` principal support for the
existing `/api/authorization/workspace-access/current` proving slice.
Persisted service-account support MUST include only the minimal metadata required to identify and
operate the service-account actor on that slice.
This change MUST additionally implement persisted `API_KEY` principal support for the existing
`/api/authorization/workspace-access/current` proving slice.
Persisted `API_KEY` principal support MUST include only the minimal metadata required to identify
and operate the API-key actor on that slice.
SYSTEM, INTEGRATION, and AGENT executable authentication behavior MUST remain deferred beyond the
minimal contracts needed to preserve model stability.
(Previously: `API_KEY`, `SYSTEM`, `INTEGRATION`, and `AGENT` executable authentication behavior
remained deferred.)

#### Scenario: Persisted API-key principal is available for authenticated requests

- GIVEN an API key exists as a persisted credential bound to a persisted principal with the minimal
  required companion metadata
- WHEN the platform resolves an authenticated request for that actor on
  `/api/authorization/workspace-access/current`
- THEN the principal MUST be represented as type `API_KEY`
- AND downstream behavior MUST consume the repo-local principal rather than raw credential
  structures

#### Scenario: Deferred principal types remain deferred beyond API keys and service accounts

- GIVEN this change activates executable support for `API_KEY` principals on the existing proving
  slice
- WHEN identity scope is reviewed
- THEN `SYSTEM`, `INTEGRATION`, and `AGENT` executable authentication behavior MUST remain deferred
- AND the principal taxonomy MUST remain stable without broadening this slice

### Requirement: JWT-First Identity Materialization for Phase One

The system MUST support repo-local authenticated principal materialization for the proving slice.

The system MUST derive the authenticated principal identity from a validated credential through
repo-local identity seams.
For USER principals on the proving slice, the system MUST continue to materialize the authenticated
principal from a validated JWT.
For SERVICE_ACCOUNT principals on the proving slice, the system MUST materialize the authenticated
principal from the validated service-account bearer credential path.
For API_KEY principals on the proving slice, the system MUST materialize the authenticated principal
from the validated API-key credential path.
The system MUST treat credential transport as an authentication and principal materialization seam,
not as the source of authorization truth.
The system MUST NOT rely on credential claims or credential presence alone to determine workspace
membership, permission grants, or effective authorization.
Federated social login, external account linking, broader identity lifecycle breadth, and
generalized multi-principal onboarding flows remain deferred.
(Previously: Only USER and SERVICE_ACCOUNT executable materialization were required on the proving
slice.)

#### Scenario: Valid API key materializes an authenticated principal

- GIVEN a protected request to `/api/authorization/workspace-access/current` includes a valid API
  key bound to a persisted principal
- WHEN the platform authenticates the request
- THEN the system MUST materialize an authenticated principal with principal type `API_KEY`
- AND downstream application behavior MUST consume the platform principal rather than raw credential
  structures

#### Scenario: Missing, invalid, or non-materializable API key blocks principal establishment

- GIVEN a protected request to `/api/authorization/workspace-access/current` includes a missing,
  invalid, inactive, revoked, or non-materializable API key
- WHEN the platform evaluates authentication
- THEN the system MUST reject the request as unauthenticated
- AND no protected use case MUST execute
