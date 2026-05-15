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
The principal taxonomy MUST be part of the stable platform model even if phase one authenticates
only a subset of principal types.
Phase one MUST implement the USER principal path for the proving slice.
Support for SERVICE_ACCOUNT, API_KEY, SYSTEM, INTEGRATION, and AGENT principals is platform-required
but deferred beyond the minimal contracts needed to preserve model stability.

#### Scenario: Phase-one request uses supported principal type

- GIVEN a protected phase-one request is authenticated successfully
- WHEN the principal is materialized for downstream behavior
- THEN the platform MUST represent that actor as a principal with type USER
- AND the principal MUST flow through repo-local identity seams rather than raw framework-specific
  types

#### Scenario: Deferred principal types remain part of the model

- GIVEN phase one does not yet authenticate SERVICE_ACCOUNT or AGENT principals end to end
- WHEN the identity model is defined
- THEN those principal types MUST remain part of the canonical taxonomy
- AND later implementation MAY add them without redefining identity semantics

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

The system MUST support JWT-first authentication for phase one.

The system MUST derive the authenticated principal identity from a validated JWT for the proving
slice.
The system MUST treat JWT as an authentication transport and principal materialization seam, not as
the source of authorization truth.
The system MUST NOT rely on JWT claims alone to determine workspace membership, permission grants,
or effective authorization.
Federated social login, external account linking, and broader identity lifecycle breadth are
deferred.

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
