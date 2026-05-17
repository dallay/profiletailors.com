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
API_KEY, SYSTEM, INTEGRATION, and AGENT executable authentication behavior MUST remain deferred
beyond the minimal contracts needed to preserve model stability.

(Previously: Phase one implemented only the USER principal path for the proving slice, and support
for SERVICE_ACCOUNT, API_KEY, SYSTEM, INTEGRATION, and AGENT principals remained deferred beyond
minimal model-stability contracts.)

#### Scenario: Persisted service-account principal is available for authenticated requests

- GIVEN a service account exists as a persisted principal with the minimal required companion
  metadata
- WHEN the platform resolves an authenticated request for that actor on
  `/api/authorization/workspace-access/current`
- THEN the principal MUST be represented as type `SERVICE_ACCOUNT`
- AND downstream behavior MUST consume the repo-local principal rather than raw framework token
  structures

#### Scenario: Deferred principal types remain deferred beyond service accounts

- GIVEN this change activates executable support for `SERVICE_ACCOUNT` principals
- WHEN identity scope is reviewed
- THEN `API_KEY`, `SYSTEM`, `INTEGRATION`, and `AGENT` executable authentication behavior MUST
  remain deferred
- AND the principal taxonomy MUST remain stable without broadening this slice

### Requirement: JWT-First Identity Materialization for Phase One

The system MUST support repo-local authenticated principal materialization for the proving slice.

The system MUST derive the authenticated principal identity from a validated credential through
repo-local identity seams.
For USER principals on the proving slice, the system MUST continue to materialize the authenticated
principal from a validated JWT.
For SERVICE_ACCOUNT principals on the proving slice, the system MUST materialize the authenticated
principal from the validated service-account bearer credential path.
The system MUST treat credential transport as an authentication and principal materialization seam,
not as the source of authorization truth.
The system MUST NOT rely on credential claims or credential presence alone to determine workspace
membership, permission grants, or effective authorization.
Federated social login, external account linking, broader identity lifecycle breadth, and
generalized multi-principal onboarding flows remain deferred.

(Previously: The system supported JWT-first authentication for phase one, derived the authenticated
principal identity from a validated JWT for the proving slice, and limited executable identity
materialization to the USER/JWT path.)

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
