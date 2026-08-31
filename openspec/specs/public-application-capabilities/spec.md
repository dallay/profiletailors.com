# Public Application Capabilities Specification

## Purpose

Define the minimum public runtime contract needed by unauthenticated clients to reflect
registration, password-recovery, and invitation-acceptance availability.

## Requirements

### Requirement: Allow-Listed Public Capability

`GET /api/capabilities/public` MUST be unauthenticated and MUST return exactly
`{ "registrationEnabled": boolean, "passwordRecoveryEnabled": boolean, "invitationAcceptanceEnabled": boolean }`.
Each value MUST reflect the authoritative backend capability used to enforce its operation. The
response MUST NOT expose SSO providers, generic configuration, environment metadata, secrets, or
additional settings.

#### Scenario: Capabilities report disabled features

- GIVEN registration and password recovery are disabled and invitation acceptance is enabled
- WHEN a client requests the public capabilities endpoint
- THEN the response MUST contain the registration and password-recovery fields set to false
- AND the invitation-acceptance field MUST be true
- AND no other field, including SSO data, MUST be present

#### Scenario: Capabilities report enabled features

- GIVEN registration, password recovery, and invitation acceptance are enabled
- WHEN a client requests the public capabilities endpoint
- THEN the response MUST contain all three allow-listed fields set to true
- AND no non-allow-listed configuration MUST be present

### Requirement: Defensive Client Normalization

The SPA MUST accept capability fields only when their runtime values are booleans. Missing,
malformed, or failed responses MUST normalize each restricted capability to false. Concurrent loads
MUST share one request, retry MUST be possible, and login MUST remain usable while resolution is
pending or failed.

#### Scenario: Malformed response fails closed

- GIVEN any capability is absent or not boolean
- WHEN the response is normalized
- THEN that capability MUST be false
- AND the corresponding restricted UI MUST NOT become available

#### Scenario: Loading does not block login

- GIVEN capabilities are pending or fail
- WHEN `/login` renders
- THEN email/password login MUST remain usable
- AND capability-dependent links MUST remain hidden
