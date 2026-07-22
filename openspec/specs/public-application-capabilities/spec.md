# Public Application Capabilities Specification

## Purpose

Define the minimum public runtime contract needed by unauthenticated clients to reflect registration
availability.

## Requirements

### Requirement: Allow-Listed Public Capability

`GET /api/capabilities/public` MUST be unauthenticated and MUST return exactly
`{ "registrationEnabled": boolean }`. It MUST NOT expose generic configuration, environment
metadata, secrets, or additional settings.

#### Scenario: Capability reports disabled registration

- GIVEN registration is disabled
- WHEN a client requests the public capabilities endpoint
- THEN the response MUST be successful with `registrationEnabled: false`
- AND no non-allow-listed configuration MUST be present

#### Scenario: Capability reports enabled registration

- GIVEN registration is enabled
- WHEN a client requests the public capabilities endpoint
- THEN the response MUST be successful with `registrationEnabled: true`
- AND no non-allow-listed configuration MUST be present
