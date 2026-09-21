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

### Requirement: Metadata redaction enforcement in publish ()

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

### Requirement: Per-entry bulk audit (DALLAY-665)

The system MUST publish one audit event per requested entry: `WAITLIST_ENTRY_INVITED/SUCCEEDED` per
`invited` entry, `REJECTED` plus stable code per `skipped` entry, `FAILED` plus stable code per
`failed` entry (including unexpected errors). `SUCCEEDED` audits MUST commit atomically with their
entry's state change; `REJECTED` and `FAILED` audits MUST commit independently of entry state.
Metadata MUST carry IDs and codes only — never raw tokens or emails.

#### Scenario: Success audited per entry

- GIVEN 2 entries bulk-invited successfully
- WHEN the batch completes
- THEN 2 `WAITLIST_ENTRY_INVITED/SUCCEEDED` events exist, one per entry

#### Scenario: Failed entry still audited with code

- GIVEN a batch containing 1 CONVERTED entry
- WHEN the bulk invite runs
- THEN that entry yields a `FAILED` audit event carrying `ENTRY_ALREADY_CONVERTED` despite no state
  change

### Requirement: User-control audit outcomes

The platform-admin audit stream MUST support `USER_DISABLED`, `USER_ENABLED`, and
`USER_SESSIONS_REVOKED` actions. Each attempted user-control command MUST produce one audit event
carrying operator, target principal, occurred time, action, result, and correlation context when
available. Success events MUST be emitted only after the state/session operation completes;
rejected and failed outcomes MUST carry the non-sensitive reason. Audit metadata MUST use the
existing redaction enforcement.

#### Scenario: Successful control is audited

- GIVEN an authorized disable command completes
- WHEN the audit stream is inspected
- THEN one `USER_DISABLED` event exists with a successful result

#### Scenario: Rejected control is audited without details

- GIVEN an unauthorized control attempt
- WHEN the audit stream is inspected
- THEN one rejected event exists carrying only the non-sensitive reason

### Requirement: Configuration-change audit outcomes (dallay/profiletailors.com#672)

The platform-admin audit stream MUST support a `CONFIGURATION_CHANGED` action. Each attempted
registration-mode change MUST produce one audit event carrying the operator, occurred time, action,
result, and metadata `previousMode`/`newMode` (successful attempts) or a non-sensitive rejection
reason (denied/failed attempts). Successful events MUST be emitted only after the persisted mode
change commits; the previous/new pair MUST be captured atomically with the same statement that
performs the update, so it always reflects the actual transition. `previousMode` and `newMode`
values (`OPEN`, `INVITE_ONLY`, `CLOSED`) MUST pass through the existing redaction enforcement
unredacted — they are operational state, not sensitive material.

#### Scenario: Successful configuration change is audited unredacted

- GIVEN an authorized owner changes the registration mode from `OPEN` to `CLOSED`
- WHEN the audit stream is inspected
- THEN one `CONFIGURATION_CHANGED` event exists with result `SUCCEEDED`
- AND its metadata contains `previousMode: "OPEN"` and `newMode: "CLOSED"`, both stored unredacted

#### Scenario: Rejected control is audited without disclosing prior state

- GIVEN an unauthorized (non-owner) operator attempts to change the registration mode
- WHEN the audit stream is inspected
- THEN one `CONFIGURATION_CHANGED` event exists with result `REJECTED`
- AND the persisted registration mode did not change

#### Scenario: Invalid value attempt is audited as failed

- GIVEN an authorized owner submits a value outside `OPEN`, `INVITE_ONLY`, `CLOSED`
- WHEN the audit stream is inspected
- THEN one `CONFIGURATION_CHANGED` event exists with result `FAILED`
- AND no success event is emitted
- AND the persisted registration mode did not change
