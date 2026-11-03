# Delta for Consent Scopes

## MODIFIED Requirements

### Requirement: Exclusive Real-Entity Consent Scope

Consent MUST belong to exactly one `WORKSPACE`, `WAITLIST`, or `PLATFORM` scope. A `WORKSPACE` scope MUST identify an existing workspace UUID. A `WAITLIST` scope MUST identify an existing list UUID and MUST NOT create or simulate a tenant. A `PLATFORM` scope MUST identify platform-level consent and MUST NOT refer to a workspace or waitlist. The semantic waitlist key MUST remain a semantic key; it MUST NOT replace a workspace UUID reference or serve as an inferred numeric identity.
(Previously: consent was limited to `WORKSPACE` or `WAITLIST` scopes identified by existing entity UUIDs.)

#### Scenario: Public capture

- GIVEN an existing waitlist
- WHEN public capture records consent
- THEN the scope MUST be `WAITLIST` with the real list UUID and no workspace reference
- AND capture MUST NOT fabricate a tenant

#### Scenario: First platform owner accepts current terms

- GIVEN the first platform-owner bootstrap recipient accepts the current terms version
- WHEN the acceptance is recorded
- THEN consent MUST use `PLATFORM` scope and identify the accepting recipient and terms version
- AND it MUST NOT require or create a workspace or membership

### Requirement: Tenant Endpoint Scope Security

Existing tenant endpoints MUST retain authorization and `WORKSPACE` scope. They MUST NOT permit access to `WAITLIST` or `PLATFORM` consent records because those records are not workspace consent.
(Previously: tenant endpoints were explicitly prohibited from accessing `WAITLIST` records because their workspace reference is null.)

#### Scenario: Tenant security for public consent

- GIVEN public `WAITLIST` consent exists
- WHEN a user queries or withdraws workspace consent through tenant endpoints
- THEN the endpoints MUST NOT return or modify public consent records

#### Scenario: Tenant security for platform consent

- GIVEN `PLATFORM` consent exists for bootstrap terms acceptance
- WHEN a user queries or withdraws workspace consent through tenant endpoints
- THEN the endpoints MUST NOT return or modify the platform consent record
- AND existing workspace authorization and scope restrictions MUST remain in effect
