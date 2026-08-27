# Proposal: Registration Policy Modes

## Intent

Replace the boolean registration gate with one mutually exclusive runtime policy that can safely
represent open registration, invite-only registration, and closed registration.

## Scope

### In Scope

- Define `OPEN`, `INVITE_ONLY`, and `CLOSED` registration modes.
- Evaluate the mode in the registration application path before any mutation or side effect.
- Bind the mode from typed non-secret configuration with a fail-closed default.
- Preserve the existing public capability contract by advertising public registration only in
  `OPEN` mode.
- Add domain, application, configuration, HTTP, and BDD coverage.

### Out of Scope

- Back Office configuration UI or runtime mutation commands.
- Invitation issuance, token persistence, or invitation acceptance account creation.
- Changes to login, refresh, logout, verification, or password recovery.

## Policy Semantics

`OPEN` allows the existing public registration flow. `INVITE_ONLY` denies a direct public
registration attempt with an invitation-required problem until a later invitation-aware registration
slice supplies a validated invitation context. `CLOSED` denies public registration as unavailable.

The backend remains authoritative. The public capability continues to expose only
`registrationEnabled`, which is `true` for `OPEN` and `false` for the restricted modes.

## Configuration

Use `app.identity.registration.mode` bound from `SMP_REGISTRATION_MODE`, defaulting to `CLOSED`.
Deployment examples must pass the same mode variable and must not retain the boolean registration
switch.

## Success Criteria

- [x] All three modes are represented by one typed policy.
- [x] Direct registration cannot mutate state in `INVITE_ONLY` or `CLOSED` mode.
- [x] `OPEN` mode preserves the existing registration transaction and session behavior.
- [x] Public capability and configuration bindings are covered by automated tests.
