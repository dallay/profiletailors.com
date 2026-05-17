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
This change MUST additionally implement API-key authentication for the existing
`/api/authorization/workspace-access/current` proving slice only.
For this change, persisted API-key credential state MUST include only the minimum authoritative
fields required to securely identify a credential record for lookup, verify the presented secret
without plaintext or reversible secret persistence, bind the credential to one persisted principal,
and determine whether the credential is active or revoked.
The implemented API-key path MUST validate a presented API key against authoritative backend
credential state through lookup plus verifier comparison before protected access is granted.
The implemented API-key path MUST deny a presented API key when authoritative backend credential
state is inactive or revoked.
API-key issuance, admin CRUD, rotation workflows, inventory surfaces, broad metadata expansion, and
generalized credential-platform redesign MUST remain deferred.
Full credential rotation workflows, credential families, dual-active rollover, and broad lifecycle
automation MUST remain deferred.
Deferred implementation MUST NOT remove any credential concept from the canonical platform model.
The platform SHOULD preserve a path for future external federation or provider-backed token
validation without redefining the core Credentials context.
(Previously: API key authentication, API key storage, API key lookup, and API key request handling
remained deferred.)

#### Scenario: API key is validated for the existing proving slice

- GIVEN a protected request to `/api/authorization/workspace-access/current` presents an API key
  that matches a persisted API-key credential record
- AND the persisted credential record is active
- WHEN the platform validates the credential
- THEN the platform MUST authenticate the request through the API-key credential path
- AND the proving slice MAY continue into identity and authorization evaluation

#### Scenario: Revoked API key is denied before protected access

- GIVEN a protected request to `/api/authorization/workspace-access/current` presents an API key
  that would otherwise match a persisted API-key credential record
- AND authoritative backend credential state marks that credential as revoked or inactive
- WHEN the platform evaluates authentication for the request
- THEN the platform MUST reject the request as unauthenticated or invalid for the protected slice
- AND the protected use case MUST NOT execute

#### Scenario: API-key management breadth remains deferred in this change

- GIVEN a requested capability requires API-key issuance, rotation, inventory, or operator-facing
  management behavior
- WHEN the credential scope for this change is evaluated
- THEN the platform MUST treat that capability as deferred
- AND the proving slice specification MUST proceed without those requirements
