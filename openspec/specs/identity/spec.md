# Identity Specification

## Purpose

Define platform identity semantics for the reusable IAM foundation. This specification establishes
the principal taxonomy, identity ownership boundaries, and the phase-one identity behaviors required
to support JWT-first authentication for the proving slice without collapsing the platform into a
human-user-only model.

## Requirements

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

#### Scenario: Phase-one request uses supported principal type

- GIVEN a protected phase-one request is authenticated successfully
- WHEN the principal is materialized for downstream behavior
- THEN the platform MUST represent that actor as a principal with type USER
- AND the principal MUST flow through repo-local identity seams rather than raw framework-specific
  types

#### Scenario: Persisted service-account principal is available for authenticated requests

- GIVEN a service account exists as a persisted principal with the minimal required companion
  metadata
- WHEN the platform resolves an authenticated request for that actor on
  `/api/authorization/workspace-access/current`
- THEN the principal MUST be represented as type `SERVICE_ACCOUNT`
- AND downstream behavior MUST consume the repo-local principal rather than raw framework token
  structures

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

### Requirement: Principal Identity Independence

The system MUST keep principal identity semantics independent from credential transport and
authorization outcome.

Identity MUST answer who the principal is.
Credentials MUST answer how the principal authenticated.
Authorization MUST answer what the principal may do in a resource context.
The platform MUST NOT collapse these concerns into one undifferentiated token-based model.

#### Scenario: Identity remains distinct from authorization

- GIVEN a valid authenticated principal is established
- WHEN authorization is later evaluated for a protected capability
- THEN the platform MUST treat principal identity as separate from role, grant, or permission
  outcomes
- AND authentication success alone MUST NOT imply access

#### Scenario: Identity remains distinct from credential type

- GIVEN the platform may later support multiple credential forms for different principal types
- WHEN the identity contracts are defined
- THEN the principal identity model MUST remain stable across credential mechanisms
- AND a change in credential transport MUST NOT require redefining the principal taxonomy

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

#### Scenario: Valid JWT materializes an authenticated principal

- GIVEN a protected phase-one request includes a valid JWT
- WHEN the platform authenticates the request
- THEN the system MUST materialize an authenticated principal through repo-local identity seams
- AND downstream application behavior MUST consume the platform principal rather than raw token
  structures

#### Scenario: Invalid JWT blocks protected identity establishment

- GIVEN a protected request includes a missing or invalid JWT
- WHEN the platform evaluates authentication
- THEN the system MUST reject the request as unauthenticated
- AND no protected use case MUST execute

#### Scenario: Valid service-account bearer credential materializes an authenticated principal

- GIVEN a protected request to `/api/authorization/workspace-access/current` includes a valid
  service-account bearer credential
- WHEN the platform authenticates the request
- THEN the system MUST materialize an authenticated principal with principal type `SERVICE_ACCOUNT`
- AND downstream application behavior MUST consume the platform principal rather than raw credential
  structures

#### Scenario: Missing or invalid service-account credential blocks principal establishment

- GIVEN a protected request to `/api/authorization/workspace-access/current` includes a missing,
  invalid, or non-materializable service-account bearer credential
- WHEN the platform evaluates authentication
- THEN the system MUST reject the request as unauthenticated
- AND no protected use case MUST execute

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
