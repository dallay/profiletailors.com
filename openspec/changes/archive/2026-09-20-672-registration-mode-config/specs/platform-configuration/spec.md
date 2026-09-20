# Platform Configuration Specification

## Purpose

Expose a narrow Back Office surface for approved runtime operational configuration. Registration
mode (`OPEN` / `INVITE_ONLY` / `CLOSED`) is the first and only value covered — NOT a generic
environment-variable editor.

## Requirements

### Requirement: Read current registration mode

An operator with `platform.configuration.read` MUST be able to fetch the effective registration
mode — the same value `RegistrationPolicy.evaluate()` would use at that instant, never a stale copy.

#### Scenario: Authorized read returns current mode

- GIVEN an operator holds `platform.configuration.read`
- WHEN the operator requests the current registration mode
- THEN the response MUST be `200 OK` containing one of `OPEN`, `INVITE_ONLY`, `CLOSED`

#### Scenario: Unauthorized read is denied

- GIVEN a principal has no active platform role, or a role without `platform.configuration.read`
- WHEN that principal requests the current registration mode
- THEN the response MUST be `401` (unauthenticated) or `403` (forbidden)
- AND no configuration value MUST be disclosed

### Requirement: Change registration mode is OWNER-only

Only an operator with `platform.configuration.manage` (granted solely to `PLATFORM_OWNER`) MUST be
able to change the mode. The value MUST be one of `OPEN`, `INVITE_ONLY`, `CLOSED`; any other value
MUST be rejected before any mutation, leaving the stored mode unchanged.

#### Scenario: Owner changes mode successfully

- GIVEN an operator holds `platform.configuration.manage`
- AND the current mode is `OPEN`
- WHEN the operator submits a change to `INVITE_ONLY`
- THEN the response MUST be `200 OK` and the persisted mode MUST become `INVITE_ONLY`
- AND the next `RegistrationPolicy.evaluate()` call MUST observe `INVITE_ONLY`

#### Scenario: Denied writes cause no state change

- GIVEN either (a) an operator holds `platform.configuration.read` but not `.manage` (e.g.
  `PLATFORM_OPERATOR`, `SUPPORT_AGENT`, `AUDITOR`), or (b) an owner submits a value outside `OPEN`,
  `INVITE_ONLY`, `CLOSED`
- WHEN the write is attempted
- THEN the response MUST be `403` (case a) or `400` (case b)
- AND the persisted mode MUST NOT change and no `SUCCEEDED` audit event MUST be emitted

### Requirement: Atomic update prevents lost updates

The write path MUST capture the previous and new mode atomically in one statement (`UPDATE ...
RETURNING` or an optimistic-lock version column), so concurrent writes never silently lose an
operator's change or produce an audit pair inconsistent with the actual transition.

#### Scenario: Concurrent writes never lose an update

- GIVEN the current mode is `OPEN`
- WHEN two owners concurrently submit `INVITE_ONLY` and `CLOSED` respectively
- THEN exactly one write MUST determine the final persisted mode (one of the two submitted values,
  never torn or default)
- AND each successful write's audit event MUST carry a previous/new pair matching the transition it
  actually performed

### Requirement: Durable across restart and redeploy

The registration mode MUST persist in the database, not only in process memory. A `backend` restart
or redeploy MUST NOT revert an admin-set mode back to the `SMP_REGISTRATION_MODE` seed/fallback.

#### Scenario: Admin-set mode survives a redeploy

- GIVEN an owner has changed the mode to `CLOSED` via the Back Office
- AND `SMP_REGISTRATION_MODE` is still configured to `OPEN`
- WHEN the `backend` service restarts
- THEN the effective mode after restart MUST remain `CLOSED`

### Requirement: Configuration changes are audited with previous and new values

Every attempted mode change MUST produce exactly one `CONFIGURATION_CHANGED` audit event carrying
the operator, occurred time, result, and metadata `previousMode`/`newMode` (or the non-sensitive
rejection reason for denied/failed attempts). Mode keys/values MUST NOT be redacted — they contain
no secret, token, or credential material.

#### Scenario: Successful change is audited unredacted

- GIVEN an owner changes the mode from `OPEN` to `CLOSED`
- WHEN the audit stream is inspected
- THEN one `CONFIGURATION_CHANGED`/`SUCCEEDED` event exists
- AND its metadata contains `previousMode: "OPEN"` and `newMode: "CLOSED"`, both unredacted

#### Scenario: Rejected write attempt is still audited

- GIVEN a non-owner operator attempts to change the mode
- WHEN the request is denied
- THEN one `CONFIGURATION_CHANGED`/`REJECTED` event exists and no persisted mode change occurred
