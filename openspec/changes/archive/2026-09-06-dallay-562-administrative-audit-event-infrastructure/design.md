# Design: Administrative Audit Event Infrastructure

## Technical Approach

Consolidate audit event infrastructure on the existing live `platformadmin` seam by adding
redaction enforcement inside `R2dbcAdminAuditRepository.publish()`. The orphaned
`administrative` bounded context is deleted entirely. No new domain models, no new ports,
no new tables — only a utility function and a single enforcement point in the existing
persistence adapter.

## Architecture Decisions

### Decision: Redaction inside repository, not at domain model

**Choice**: Apply `redact()` as the last operation inside `R2dbcAdminAuditRepository.publish()`
before binding metadata to the SQL statement.

**Alternatives considered**: Enforcement at `AdminAuditEvent` construction in the domain layer.
Rejected because handlers already construct `AdminAuditEvent` with raw metadata; modifying all
14 handler call sites to pre-sanitize is higher surface area and higher risk than a single
infrastructure enforcement point.

**Rationale**: Single enforcement point, no handler changes required, backwards compatible.

### Decision: Pure top-level `redact()` function

**Choice**: `redact()` is a public top-level function in the infrastructure layer,
returning a new map with sensitive keys removed.

**Alternatives considered**: A Spring component or class. Rejected because the logic is
stateless and trivially testable as a pure function.

**Rationale**: No injected state, easy to test, visible in the same file as the enforcement point.

### Decision: Case-insensitive substring match on key names

**Choice**: Any map key whose lowercase form contains any denylist substring is excluded.

**Rationale**: Matches the orphaned implementation and covers camelCase variants like
`accessToken`, `resetPassword`, `userToken`.

## Data Flow

```
AdminAuditEvent (handler builds metadata with raw sensitive keys)
        │
        ▼
AdministrativeAuditPublisher.publish(event)
        │
        ▼
R2dbcAdminAuditRepository.publish(event)
        │
        ├── redact(metadata)      ← enforcement point
        ▼
        │
        ▼
 INSERT INTO platform_admin_audit_events (metadata = <redacted JSON>)
```

## Package Structure

Only the existing `platformadmin` package is touched. No new packages created.

```
server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/
├── infrastructure/
│   └── persistence/
│       ├── R2dbcAdminAuditRepository.kt    # MODIFIED: redact() in publish()
│       └── AdminAuditRepositoryUtils.kt       # NEW: redact() function
```

## Interfaces / Contracts

### `redact()` — exact implementation

Defined in `RedactSensitiveMetadata.kt` as a public top-level function.

```kotlin
private const val REDACTED_VALUE = "[REDACTED]"

private val SENSITIVE_SUBSTRINGS = listOf(
    "password",
    "secret",
    "token",
    "key",
    "credential",
    "auth",
    "bearer",
)

fun redact(metadata: Map<String, String>): Map<String, String> = metadata.mapValues { (k, v) ->
    if (SENSITIVE_SUBSTRINGS.any { sensitive -> k.lowercase().contains(sensitive) }) REDACTED_VALUE else v
}
```

The function replaces values with `"[REDACTED]"` for keys containing any case-insensitive
sensitive substring. The original map is not mutated; a new map is returned. The stored JSON
contains all original keys but with redacted values for sensitive fields.

### `R2dbcAdminAuditRepository.publish()` — modified

```kotlin
// Before (current):
suspend fun publish(event: AdminAuditEvent) {
    databaseClient.sql(INSERT)
        .bind("id", event.id)
        .bind("metadata", event.metadata)  // raw — leaks secrets
        ...
}

// After (changed):
suspend fun publish(event: AdminAuditEvent) {
    databaseClient.sql(INSERT)
        .bind("id", event.id)
        .bind("metadata", redact(event.metadata))  // sanitized
        ...
}
```

No changes to the method signature, return type, or port interface.

## Deleted: Orphaned `administrative` Context

The following packages are deleted in their entirety:

```
server/smp/src/main/kotlin/com/profiletailors/smp/administrative/
server/smp/src/test/kotlin/com/profiletailors/smp/administrative/
```

Including:
- `AdministrativeBoundedContext.kt`
- `AdministrativeAuditEvent.kt` (domain entity)
- `AdministrativeAuditEventRepository.kt` (port interface)
- `AuditEventPublisher.kt` (application service)
- `R2dbcAdministrativeAuditEventRepository.kt` (infrastructure adapter)
- All tests for the above

## Deleted: Orphaned Migration

Migration `006-create-administrative-audit-events.yaml` was **not found** in the migration
directory — the file was never committed. The orphaned `administrative` bounded context was
deleted as dead code; no rollback migration is required.

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Unit | `redact()` edge cases (sensitive keys present/absent, case sensitivity, empty map) | Plain JUnit test, no Spring context |
| Unit | `redact()` does not mutate the input map | Assert original map is unchanged after redact |
| Integration | Handler emits event with sensitive metadata → stored row has no sensitive keys | `BddDatabaseSupport` + real `DatabaseClient`; query the row after publish |

## Open Questions

- [x] Where to apply redaction: repository (chosen) vs domain model construction (rejected)
- [x] Who calls `redact()`: `R2dbcAdminAuditRepository` (chosen), not `AuditEventPublisher` or domain model
- [x] Migration 006 status: TBD — requires git history check before deciding rollback vs forward-drop
