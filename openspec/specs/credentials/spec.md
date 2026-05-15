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
Service account and API key end-to-end authentication behavior is platform-required but deferred
beyond phase one.
Deferred implementation MUST NOT remove those concepts from the canonical platform model.
The platform SHOULD preserve a path for future external federation or provider-backed token
validation without redefining the core Credentials context.

#### Scenario: JWT supports the phase-one proving slice

- GIVEN a phase-one protected request includes a valid JWT
- WHEN the credential is validated successfully
- THEN the platform MUST use that JWT path to authenticate the request
- AND the proving slice MAY continue into identity and authorization evaluation

#### Scenario: Deferred service-account concept remains explicit

- GIVEN phase one does not yet implement a service-account protected flow end to end
- WHEN the Credentials context is specified
- THEN service-account credentials MUST remain an explicit platform concept
- AND future implementation MAY add them without changing the principal taxonomy or authorization
  model
