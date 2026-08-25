# Password Recovery UI Specification

## Purpose

Preserve password-recovery security and routing while defining capability-dependent unavailable states.

## Requirements

### Requirement: In-Route Recovery Availability

Forgot-password and reset-password routes MUST wait for public capabilities before rendering a form. When recovery is disabled, unresolved after failure, or malformed, each requested route MUST fail closed, preserve its URL and query, show “Password recovery is currently unavailable,” provide named-route navigation to login, and MUST NOT send recovery requests.

#### Scenario: Enabled recovery route
- GIVEN `passwordRecoveryEnabled` resolves to true
- WHEN `/forgot-password` or `/reset-password?token=opaque` is requested
- THEN the corresponding existing form MUST render at that URL

#### Scenario: Disabled recovery route
- GIVEN `passwordRecoveryEnabled` resolves to false
- WHEN either recovery route is requested
- THEN the unavailable state MUST replace the form without redirecting
- AND no recovery or reset request MUST be sent

#### Scenario: Capability failure
- GIVEN capability loading fails
- WHEN a recovery route resolves
- THEN recovery MUST fail closed at the requested URL
- AND named navigation back to login MUST remain available

### Requirement: Session-Agnostic Reset Contract

`/reset-password` MUST remain accessible regardless of authentication session state. A valid emailed token MUST continue to be accepted under the existing reset contract; the route MUST NOT become guest-only. Tokens, raw emails, credentials, URLs containing tokens, and enumeration-sensitive details MUST NOT be logged or persisted by the client.

#### Scenario: Authenticated reset
- GIVEN an authenticated user opens `/reset-password?token=valid`
- AND password recovery is enabled
- WHEN the route renders
- THEN the reset form MUST remain accessible with the token available only for submission

#### Scenario: Token privacy
- GIVEN a reset attempt succeeds or fails
- WHEN client storage and telemetry are inspected
- THEN the token, raw email, credentials, and token-bearing URL MUST NOT be present

### Requirement: Existing Recovery Responses

Forgot-password MUST preserve generic confirmation regardless of account existence. Reset-password MUST preserve existing safe token validation, success, and error mapping; this redesign MUST NOT alter the recovery protocol.

#### Scenario: Generic forgot-password confirmation
- GIVEN recovery is enabled
- WHEN any syntactically valid email is submitted
- THEN the same generic confirmation MUST be shown regardless of account existence

#### Scenario: Invalid reset token
- GIVEN recovery is enabled and the token is invalid or expired
- WHEN reset is submitted
- THEN a safe non-enumerating error MUST be shown
- AND credentials MUST remain unchanged
