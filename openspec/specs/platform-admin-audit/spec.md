# Platform Admin Audit Specification

> Scope note (DALLAY-562, archived 2026-09-09): this spec consolidates audit event
> infrastructure on the live `platformadmin` bounded context. Redaction is enforced at the
> persistence seam (`R2dbcAdminAuditRepository.publish()`); the orphaned `administrative`
> bounded context and its table migration were removed. Where the original change delta said
> sensitive keys are "removed", the implemented and tested behavior masks values as
> `[REDACTED]` (key kept, raw value never stored) — masking satisfies the acceptance property
> that raw secrets/tokens are excluded from storage.

## Purpose

Administrative audit events (`AdminAuditEvent`, published via `AdministrativeAuditPublisher`
through `R2dbcAdminAuditRepository` into `platform_admin_audit_events`) MUST NEVER persist raw
sensitive metadata values. A single infrastructure enforcement point redacts metadata before
it is serialized and bound to the INSERT statement, so capability-slice handlers emit events
unmodified.

## Requirements

### Requirement: Metadata redaction enforcement in publish()

`R2dbcAdminAuditRepository.publish()` MUST call `redact(event.metadata)` and MUST serialize
and bind only the redacted map. The raw `event.metadata` MUST NEVER reach storage.

`redact()` MUST be a pure top-level function returning a new map:

- For every key whose lowercase form contains any denylisted substring, the stored map MUST
  contain that key with the value `[REDACTED]` instead of the raw value.
- Keys with no denylisted substring MUST be stored unchanged.
- The input map MUST NOT be mutated.
- An empty input map MUST produce an empty output map.

Denylisted substrings (case-insensitive substring match): `password`, `secret`, `token`,
`key`, `credential`, `auth`, `bearer`. Compound forms (e.g. `invitationToken`,
`resetPassword`, `accessToken`, `session_token`) are caught because they contain a listed
substring.

#### Scenario: Publish event with sensitive metadata

- GIVEN an `AdminAuditEvent` with metadata
  `{"action": "user.login", "invitationToken": "secret-value", "userId": "user-123"}`
- WHEN `R2dbcAdminAuditRepository.publish(event)` is called
- THEN the row stored in `platform_admin_audit_events` contains
  `{"action": "user.login", "invitationToken": "[REDACTED]", "userId": "user-123"}`
- AND no raw sensitive value is stored

#### Scenario: Publish event with no sensitive metadata

- GIVEN an `AdminAuditEvent` with metadata `{"action": "user.logout", "userId": "user-456"}`
- WHEN `R2dbcAdminAuditRepository.publish(event)` is called
- THEN the stored row contains all original keys intact

#### Scenario: Publish event with case-variant sensitive keys

- GIVEN an `AdminAuditEvent` with metadata
  `{"action": "auth", "accessToken": "secret-value", "RESETPassword": "another-secret"}`
- WHEN `R2dbcAdminAuditRepository.publish(event)` is called
- THEN both `accessToken` and `RESETPassword` are stored as `[REDACTED]`
- AND `action` is stored unchanged

#### Scenario: No mutation of original event

- GIVEN an `AdminAuditEvent` with metadata containing sensitive keys
- WHEN `redact(event.metadata)` is called
- THEN the original `event.metadata` map is unchanged after the call

### Requirement: Orphaned administrative context removed

The orphaned `administrative` bounded context MUST NOT exist in main or test sources, and no
code MUST reference it:

- `server/smp/src/main/kotlin/com/profiletailors/smp/administrative/` MUST be deleted
- `server/smp/src/test/kotlin/com/profiletailors/smp/administrative/` MUST be deleted
- No source under `server/smp/src/` MUST import `com.profiletailors.smp.administrative.**`

#### Scenario: Orphaned administrative context is no longer referenced

- GIVEN no code imports `com.profiletailors.smp.administrative.**`
- WHEN compilation completes
- THEN there are no dead-code references to the deleted package

### Requirement: Audit events table migration state

- The orphaned `006-create-administrative-audit-events.yaml` migration MUST be deleted and
  MUST NOT be included from `db.changelog-master.yaml` (rollback path: V006 never reached a
  shared environment).
- `007-add-metadata-to-platform-admin-audit-events.yaml` MUST add a `metadata TEXT` column
  to `platform_admin_audit_events`, and the Liquibase-master include MUST apply it.

#### Scenario: Metadata column migration applies

- GIVEN the Liquibase changelog with V007 included
- WHEN the schema migrates (verified via a Liquibase-enabled integration run)
- THEN `platform_admin_audit_events` accepts and returns event rows with metadata

## Non-Requirements

- Admin audit query API or UI — out of scope.
- Arbitrary free-text notes in audit events — out of scope.
- Persistence-layer stored-content assertion hardening (P2 follow-up): the redaction
  integration test SHOULD be strengthened to publish genuinely sensitive keys and assert on
  the stored `metadata` column directly.
- Migration file numbering hygiene (P3 informational): two `007-` filename prefixes coexist
  (`007-add-invitation-target.yaml`, `007-add-metadata-to-platform-admin-audit-events.yaml`);
  changeset IDs are unique and ordering flows from the master include, so this is harmless,
  but future migrations SHOULD use unique numeric prefixes.
