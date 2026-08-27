# Delta for Registration Policy

## ADDED Requirements (DALLAY-561)

### Requirement: Mutually Exclusive Registration Policy

The backend MUST represent registration availability as one of `OPEN`, `INVITE_ONLY`, or `CLOSED`.
The policy MUST be evaluated server-side before a public registration command can perform any
normalization, persistence, event publication, or session issuance.

#### Scenario: Open mode permits public registration

- GIVEN registration mode is `OPEN`
- WHEN a visitor submits valid registration details
- THEN the existing registration flow MUST proceed

#### Scenario: Invite-only mode rejects direct public registration

- GIVEN registration mode is `INVITE_ONLY`
- WHEN a visitor submits valid registration details without a validated invitation context
- THEN registration MUST be rejected with a safe invitation-required error
- AND no registration mutation or side effect MUST occur

#### Scenario: Closed mode rejects public registration

- GIVEN registration mode is `CLOSED`
- WHEN a visitor submits valid registration details
- THEN registration MUST be rejected as unavailable
- AND no registration mutation or side effect MUST occur

### Requirement: Typed Registration Mode Configuration

The backend MUST bind `app.identity.registration.mode` from the non-secret
`SMP_REGISTRATION_MODE` environment variable. Missing configuration MUST default to `CLOSED`.

#### Scenario: Missing mode fails closed

- GIVEN `SMP_REGISTRATION_MODE` is not configured
- WHEN the application binds identity configuration
- THEN the registration mode MUST be `CLOSED`

#### Scenario: Explicit mode binds

- GIVEN `SMP_REGISTRATION_MODE` is `INVITE_ONLY`
- WHEN the application binds identity configuration
- THEN the registration mode MUST be `INVITE_ONLY`

### Requirement: Public Capability Reflects Public Registration

The public capability response MUST report `registrationEnabled: true` only for `OPEN` mode. It
MUST report `false` for `INVITE_ONLY` and `CLOSED` without exposing additional operational state.
