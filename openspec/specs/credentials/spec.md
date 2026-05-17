# Credentials Specification

## Purpose

Define credential and token semantics for the reusable IAM platform. This specification establishes
the separation between principal identity and credential forms, plus the platform concepts for JWTs,
service accounts, API keys, and related credential paths while keeping phase-one implementation
intentionally narrow.

## Requirements

### Requirement: Credential Concepts Are Distinct from Principal and Authorization Models

The system MUST model credential concepts separately from principal identity and authorization
semantics.

Credentials MUST describe how a principal authenticates.
Credentials MUST NOT by themselves define workspace membership or effective authorization.
The Credentials context MUST preserve room for JWTs, service account credentials, API keys, and
future provider-backed credentials within a stable platform model.

#### Scenario: Credential form does not decide authorization alone

- GIVEN a principal authenticates successfully with a supported credential form
- WHEN the principal requests a protected capability
- THEN the platform MUST require downstream authorization evaluation beyond credential validation
- AND credential success alone MUST NOT grant access

#### Scenario: New credential forms fit the stable model

- GIVEN the platform later adds a new supported credential form
- WHEN the credential model is extended
- THEN the extension MUST fit within the stable Credentials context semantics
- AND the principal and authorization models MUST remain intact

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

#### Scenario: JWT supports the phase-one proving slice

- GIVEN a phase-one protected request includes a valid JWT
- WHEN the credential is validated successfully
- THEN the platform MUST use that JWT path to authenticate the request
- AND the proving slice MAY continue into identity and authorization evaluation

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
