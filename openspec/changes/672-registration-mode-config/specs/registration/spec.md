# Delta for Registration

## MODIFIED Requirements

### Requirement: Typed Registration Mode Configuration

The backend MUST source the effective registration mode from a single-row, DB-persisted
configuration value, read through on every `RegistrationPolicy.evaluate()` call with no caching
layer. `app.identity.registration.mode`, bound from the non-secret `SMP_REGISTRATION_MODE`
environment variable, becomes the seed/fallback value only: it MUST be used to seed the persisted
row on first migration and MUST be consulted only when no persisted row exists. Missing
configuration at every level MUST still default to `CLOSED`.
(Previously: the mode was bound once from `SMP_REGISTRATION_MODE` at application startup and was
the sole, immutable source of truth for the process lifetime.)

#### Scenario: Missing mode fails closed

- GIVEN `SMP_REGISTRATION_MODE` is not configured
- AND no persisted registration configuration row exists
- WHEN the application binds identity configuration and `RegistrationPolicy.evaluate()` is called
- THEN the effective registration mode MUST be `CLOSED`

#### Scenario: Explicit env value seeds the persisted row

- GIVEN `SMP_REGISTRATION_MODE` is `INVITE_ONLY`
- AND no persisted registration configuration row exists yet
- WHEN the application starts and the seeding migration runs
- THEN the persisted registration configuration MUST be seeded to `INVITE_ONLY`

#### Scenario: Persisted value overrides the property fallback

- GIVEN `SMP_REGISTRATION_MODE` is `OPEN`
- AND a persisted registration configuration row exists with mode `CLOSED`
- WHEN `RegistrationPolicy.evaluate()` is called
- THEN the effective mode MUST be `CLOSED` (the persisted row wins)

#### Scenario: No persisted row falls back to the property default

- GIVEN no persisted registration configuration row exists
- AND `SMP_REGISTRATION_MODE` is `INVITE_ONLY`
- WHEN `RegistrationPolicy.evaluate()` is called
- THEN the effective mode MUST be `INVITE_ONLY`

## ADDED Requirements

### Requirement: Registration Mode Is Runtime-Mutable Without Redeploy

`RegistrationPolicy.evaluate()` MUST become a `suspend` operation that reads the persisted mode
through on every call. An administrator-mutated mode MUST take effect on the next call — no
application restart, redeploy, or cache invalidation MUST be required, and a redeploy MUST NOT
revert an admin-set mode back to the seed/fallback value.

#### Scenario: Admin change takes effect on the next evaluation

- GIVEN the persisted registration mode is `OPEN`
- WHEN an owner changes it to `CLOSED` via the Back Office
- AND a visitor immediately attempts to register
- THEN the registration attempt MUST be evaluated against `CLOSED` and rejected as unavailable

#### Scenario: Redeploy does not revert an admin-set mode

- GIVEN an owner has set the persisted registration mode to `CLOSED`
- AND `SMP_REGISTRATION_MODE` remains configured to `OPEN`
- WHEN the `backend` service redeploys
- THEN the effective registration mode after redeploy MUST remain `CLOSED`
