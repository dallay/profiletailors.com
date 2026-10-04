# Delta for email-notifications

## MODIFIED Requirements

### Requirement: Temporary raw-token handoff is non-canonical

Until DALLAY-566 supplies the token-safe replacement, the existing handler-to-event-to-consumer
raw-token handoff MAY remain solely to render the accept URL in memory. The persisted notification
payload MUST hold template params plus delivery key only; a token-bearing `acceptUrl` MUST NOT be
stored unless DALLAY-565 owners scope it as signed delivery-surface debt in design.md with removal
tracked by DALLAY-566. This delta MUST NOT authorize new token surfaces. DALLAY-566 owns generation,
rotation, TTL, validation, recipient binding, URL assembly, encoding, and the replacement handoff.
(Previously: tolerated token-bearing acceptUrl in persisted payload without requiring scoping or
sign-off.)

#### Scenario: Temporary exception remains visible

- GIVEN DALLAY-566's replacement handoff is not yet implemented
- WHEN direct invitation delivery is restored
- THEN the existing raw-token path is explicitly marked temporary and non-canonical
- AND no HTTP response, audit record, log, metric, or new durable field exposes the token
- AND the DALLAY-566 follow-up remains required

#### Scenario: Persisted payload holds no bearer

- GIVEN a committed invitation delivery event
- WHEN the notification record is persisted
- THEN the payload MUST contain template params and delivery key only, with no token-bearing URL
  unless scoped as signed debt
