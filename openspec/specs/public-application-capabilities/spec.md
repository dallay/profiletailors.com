# Public Application Capabilities Specification

## Purpose

Define the minimum public runtime contract needed by unauthenticated clients to reflect registration
and password-recovery availability.

## Requirements

### Requirement: Allow-Listed Public Capability

`GET /api/capabilities/public` MUST be unauthenticated and MUST return exactly
`{ "registrationEnabled": boolean, "passwordRecoveryEnabled": boolean }`. Both values MUST reflect
the authoritative backend configurations used to enforce their operations. The response MUST NOT
expose SSO providers, generic configuration, environment metadata, secrets, or additional settings.

#### Scenario: Capabilities report disabled features

- GIVEN registration and password recovery are disabled
- WHEN a client requests the public capabilities endpoint
- THEN the response MUST contain both allow-listed fields set to false
- AND no other field, including SSO data, MUST be present

#### Scenario: Capabilities report enabled features

- GIVEN registration and password recovery are enabled
- WHEN a client requests the public capabilities endpoint
- THEN the response MUST contain both allow-listed fields set to true
- AND no non-allow-listed configuration MUST be present

### Requirement: Defensive Client Normalization

The SPA MUST accept capability fields only when their runtime values are booleans. Missing,
malformed, or failed responses MUST normalize both restricted capabilities to false. Concurrent
loads MUST share one request, retry MUST be possible, and login MUST remain usable while resolution
is pending or failed.

#### Scenario: Malformed response fails closed

- GIVEN either capability is absent or not boolean
- WHEN the response is normalized
- THEN that capability MUST be false
- AND restricted UI MUST NOT become available

#### Scenario: Loading does not block login

- GIVEN capabilities are pending or fail
- WHEN `/login` renders
- THEN email/password login MUST remain usable
- AND capability-dependent links MUST remain hidden
