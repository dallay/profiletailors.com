# Delta for Credentials

## MODIFIED Requirements

### Requirement: JWT, Service Account, and API Key Platform Concepts

The platform MUST recognize JWT tokens, service accounts, and API keys as first-class credential
concepts in the target architecture.

Phase one MUST implement JWT-backed authentication for USER principals in the proving slice.
This change MUST additionally implement bearer-based service-account authentication for the existing
`/api/authorization/workspace-access/current` proving slice.
The implemented service-account path MUST validate a presented bearer credential against
authoritative backend credential state before protected access is granted.
The implemented service-account path MUST support authoritative revocation enforcement for that
credential path.
API key authentication, API key storage, API key lookup, and API key request handling MUST remain
deferred.
Full credential rotation workflows, credential families, dual-active rollover, and broad lifecycle
automation MUST remain deferred.
Deferred implementation MUST NOT remove any credential concept from the canonical platform model.
The platform SHOULD preserve a path for future external federation or provider-backed token
validation without redefining the core Credentials context.

(Previously: Phase one implemented only JWT-backed authentication for USER principals in the proving
slice, while service account and API key end-to-end authentication behavior remained deferred beyond
phase one.)

#### Scenario: Service-account bearer credential is validated for the proving slice

- GIVEN a protected request to `/api/authorization/workspace-access/current` presents a bearer
  credential for a persisted service account
- WHEN the credential is validated successfully against authoritative backend credential state
- THEN the platform MUST treat the request as authenticated through the service-account credential
  path
- AND the proving slice MAY continue into identity and authorization evaluation

#### Scenario: Revoked service-account credential is denied before protected access

- GIVEN a protected request to `/api/authorization/workspace-access/current` presents a bearer
  credential that is otherwise structurally valid for a persisted service account
- AND authoritative backend credential state marks that credential as revoked
- WHEN the platform evaluates authentication for the request
- THEN the platform MUST reject the request as unauthenticated or invalid for the protected slice
- AND the protected use case MUST NOT execute

#### Scenario: API-key expansion remains deferred in this change

- GIVEN a request for this change includes API key authentication or management behavior
- WHEN the credential scope is evaluated
- THEN the platform MUST treat that behavior as deferred
- AND the proving slice specification MUST proceed without API key requirements
