# Specification: Administrative Audit Event Infrastructure

## Overview

Consolidate audit event infrastructure on the existing `platformadmin` bounded context by adding
redaction enforcement inside `R2dbcAdminAuditRepository.publish()`. The orphaned `administrative`
bounded context and its `administrative_audit_events` table are removed.

## Changes

### MODIFIED: Redaction Enforcement

**Element**: `R2dbcAdminAuditRepository.publish()`

The method now calls `redact()` on event metadata before storing it.

**Before**:
```kotlin
suspend fun publish(event: AdminAuditEvent) {
    // stores event.metadata directly — sensitive keys may be stored in plain text
}
```

**After**:
```kotlin
suspend fun publish(event: AdminAuditEvent) {
    val safeMetadata = redact(event.metadata)
    // stores safeMetadata — sensitive keys removed before INSERT
}
```

**Sensitive key substrings** (case-insensitive match):
`password`, `token`, `secret`, `credential`, `key`, `invitationtoken`, `resettoken`,
`refreshtoken`, `acresstoken`

**Stored data**:
- `id`, `event_type`, `workspace_id`, `actor_id`, `timestamp` — unchanged
- `metadata` — only non-sensitive key-value pairs

### REMOVED: Orphaned `administrative` Context

**Elements deleted**:

| Element | Type | Location |
|---|---|---|
| `AdministrativeBoundedContext` | class | `domain/AdministrativeBoundedContext.kt` |
| `AdministrativeAuditEvent` | class | `domain/AdministrativeAuditEvent.kt` |
| `AdministrativeAuditEventRepository` | interface | `domain/AdministrativeAuditEventRepository.kt` |
| `AuditEventPublisher` | class | `application/AuditEventPublisher.kt` |
| `R2dbcAdministrativeAuditEventRepository` | class | `infrastructure/R2dbcAdministrativeAuditEventRepository.kt` |
| `AdministrativeAuditEventRepositoryImplTest` | test | `infrastructure/R2dbcAdministrativeAuditEventRepositoryImplTest.kt` |
| `AuditEventPublisherTest` | test | `application/AuditEventPublisherTest.kt` |

**Location**: `server/smp/src/main/kotlin/com/profiletailors/smp/administrative/` and
`server/smp/src/test/kotlin/com/profiletailors/smp/administrative/`

### REMOVED: Orphaned Table Migration

**Option A — Rollback** (if V006 was never deployed to shared environments):
Migration file `V006__create_administrative_audit_events.sql` is deleted from `db/migration/`.
The include entry is removed from `db/changelog-master.yaml`.

**Option B — Forward-drop** (if V006 was already applied to shared environments):
A new migration `V007__drop_administrative_audit_events.sql` is added:
```sql
DROP TABLE IF EXISTS administrative_audit_events;
```
The include entry is removed from `db.changelog-master.yaml`.

Which option applies is determined by checking whether `V006__create_administrative_audit_events.sql`
was merged and deployed before the consolidation decision.

## Scenarios

### Scenario: Publish event with sensitive metadata

**Given** an `AdminAuditEvent` with metadata:
```json
{
  "action": "user.login",
  "invitationToken": "secret-value",
  "userId": "user-123"
}
```

**When** `R2dbcAdminAuditRepository.publish(event)` is called

**Then** the row stored in `platform_admin_audit_events` has:
```json
{
  "action": "user.login",
  "userId": "user-123"
}
```
The `invitationToken` key is removed by `redact()`.

### Scenario: Publish event with no sensitive metadata

**Given** an `AdminAuditEvent` with metadata:
```json
{"action": "user.logout", "userId": "user-456"}
```

**When** `R2dbcAdminAuditRepository.publish(event)` is called

**Then** the row stored in `platform_admin_audit_events` has all original keys intact.

### Scenario: Publish event with case-variant sensitive keys

**Given** an `AdminAuditEvent` with metadata:
```json
{"action": "auth", "accessToken": "secret-value", "RESETPassword": "another-secret"}
```

**When** `R2dbcAdminAuditRepository.publish(event)` is called

**Then** the row stored in `platform_admin_audit_events` has:
```json
{"action": "auth"}
```
Both `accessToken` and `RESETPassword` are removed (case-insensitive substring match on "token" and "password").

### Scenario: No mutation of original event

**Given** an `AdminAuditEvent` with metadata containing sensitive keys

**When** `redact(event.metadata)` is called

**Then** the original `event.metadata` map is unchanged after the call.

### Scenario: Orphaned `administrative` context is no longer referenced

**Given** no code imports `com.profiletailors.smp.administrative.**`

**When** compilation completes

**Then** no dead code warnings for the deleted package

## Acceptance Criteria

- [ ] `R2dbcAdminAuditRepository.publish()` calls `redact()` before storing metadata
- [ ] `redact()` removes keys containing: password, token, secret, credential, key (case-insensitive)
- [ ] `redact()` does not mutate the input map
- [ ] `server/smp/src/main/kotlin/com/profiletailors/smp/administrative/` is deleted
- [ ] `server/smp/src/test/kotlin/com/profiletailors/smp/administrative/` is deleted
- [ ] `V006__create_administrative_audit_events.sql` is either deleted (rollback) or superseded by forward-drop migration
- [ ] `backend-test-fast` passes without errors
- [ ] `backend-bdd-fast` passes without errors
- [ ] Unit test covers `redact()` edge cases: empty map, no sensitive keys, all sensitive keys, mixed case variants, original map untouched
- [ ] Integration test covers: event with sensitive metadata → stored row is sanitized
