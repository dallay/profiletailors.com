# Proposal: Administrative Audit Event Infrastructure

## Intent

Consolidate audit event infrastructure on the **existing live `platformadmin` seam**, adding redaction enforcement so sensitive metadata values never reach stored audit records. The orphaned `administrative` bounded context is deleted.

The production seam is `platformadmin`: `AdminAuditEvent`, `AdministrativeAuditPublisher` (14 callers), `R2dbcAdminAuditRepository`, and `platform_admin_audit_events`. This change adds redaction capability to that seam and removes the parallel dead code.

## Scope

### In Scope
- Add a pure `redact()` utility to `platformadmin` — applied inside `R2dbcAdminAuditRepository.publish()`, not at domain model construction
- Apply `redact()` once, immediately before serializing/binding metadata to the database
- Unit tests for the redaction policy
- Delete the orphaned `administrative` context: all packages under `server/smp/src/main/kotlin/com/profiletailors/smp/administrative/` and `server/smp/src/test/kotlin/com/profiletailors/smp/administrative/`
- Drop the orphaned `administrative_audit_events` table via Liquibase (see migration strategy below)
- Critical integration test: handler metadata with sensitive keys is published and stored without those keys

### Out of Scope
- Admin audit query API or UI
- Arbitrary free-text notes stored in audit events
- Storing raw invitation tokens, reset tokens, refresh tokens, access tokens, passwords, or secrets
- MCP tool audit (already implemented in `mcp-tool-audit`)
- `BulkPublishingController` compilation failure (pre-existing, unrelated)

## Approach

### Data Flow

```
AdminAuditEvent (handler builds metadata)
        │
        ▼
AdministrativeAuditPublisher.publish(event)
        │
        ▼
R2dbcAdminAuditRepository.publish()
        │
        ├── redact(metadata)     ← single enforcement point
        ▼
platform_admin_audit_events
```

`AdminAuditEvent` and `AdministrativeAuditPublisher` remain unchanged. Redaction is a pure function applied once in `R2dbcAdminAuditRepository.publish()` before the metadata map is serialized and bound to the INSERT statement.

### 1. Add `redact()` utility to `platformadmin`

Modeled after the orphaned `administrative` implementation. A pure top-level function in the infrastructure layer:

```kotlin
private val SENSITIVE_SUBSTRINGS = listOf(
    "password", "token", "secret", "credential", "key",
    "invitationtoken", "resettoken", "refreshtoken", "accesstoken",
)

fun redact(metadata: Map<String, String>): Map<String, String> =
    metadata.filterKeys { key ->
        SENSITIVE_SUBSTRINGS.none { substring -> key.lowercase().contains(substring) }
    }
```

The domain model (`AdminAuditEvent`) does NOT enforce redaction at construction — handlers may pass raw metadata. The repository implementation applies `redact()` as the last step before persistence.

### 2. Apply redaction in `R2dbcAdminAuditRepository.publish()`

In `R2dbcAdminAuditRepository`, modify `publish()` to call `redact(metadata)` immediately before binding to the SQL statement. No other changes to the repository signature or the port interface.

### 3. Delete orphaned `administrative` context

Delete all code under:
- `server/smp/src/main/kotlin/com/profiletailors/smp/administrative/`
- `server/smp/src/test/kotlin/com/profiletailors/smp/administrative/`

### 4. Migration strategy for `administrative_audit_events`

**Requires investigation before finalizing:**

- If migration `V006__create_administrative_audit_events` was **never applied in any shared environment**, delete the migration file and remove the include from `db.changelog-master.yaml`.
- If migration `V006` **was applied to any shared environment**, the migration is immutable: do not delete it. Instead, create a forward drop migration (e.g., `V007__drop_administrative_audit_events.sql`) that drops the orphaned table.

The changelog entry for the drop migration should be added to `db.changelog-master.yaml` regardless of which path is taken.

### 5. Tests

- Unit test `redact()` against the full `SENSITIVE_SUBSTRINGS` denylist
- Critical integration test: `AdminAuditEvent` with `accessToken`, `password`, `secretKey`, and legitimate fields → published via `R2dbcAdminAuditRepository` → `platform_admin_audit_events` row contains only legitimate fields

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/persistence/R2dbcAdminAuditRepository.kt` | Modified | Apply `redact()` in `publish()` before binding metadata |
| `server/smp/src/main/kotlin/com/profiletailors/smp/administrative/` | Deleted | Remove orphaned context entirely |
| `server/smp/src/test/kotlin/com/profiletailors/smp/administrative/` | Deleted | Remove orphaned tests |
| `db/migration/` | Modified | Drop orphaned `administrative_audit_events` table (strategy TBD — see above) |

## Comparison with Pre-Exploration Proposal

| | Old (administrative-first) | New (platformadmin-first) |
|--|---------------------------|--------------------------|
| Domain model | New `AdministrativeAuditEvent` (unused) | Existing `AdminAuditEvent` (live, 6 handlers) |
| Ports | New `AuditEventPublisher` (unwired) | Existing `AdministrativeAuditPublisher` (live, 14 callers) |
| Repository | New `R2dbcAdministrativeAuditEventRepository` (unwired) | Existing `R2dbcAdminAuditRepository` (live, tested) |
| Table | New `administrative_audit_events` (orphaned) | Existing `platform_admin_audit_events` (live) |
| Redaction | Dead code enforcement at construction | Live enforcement at persistence |
| Risk | Low adoption (no handlers) | Zero — already adopted |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Dropping `administrative_audit_events` conflicts with a shared environment that ran migration 006 | Low — requires investigation | Verify migration 006 status before finalizing strategy; use forward drop if already applied |
| Handler metadata still contains sensitive keys | Low | Add integration test verifying no sensitive keys in stored records |
| Removing `administrative` context breaks other work in progress | Medium | Verify no other branch depends on `administrative` context before merging |

## Rollback Plan

1. Revert the redaction changes in `R2dbcAdminAuditRepository` (restore raw metadata write)
2. Re-create the `administrative/` context packages from the previous commit
3. Re-add the `administrative_audit_events` table via migration
4. Re-run `backend-test-fast` to confirm clean revert

## Dependencies

- None. Uses existing R2DBC, Liquibase, and Spring Modulith patterns.

## Success Criteria

- [ ] `redact()` utility covers all `SENSITIVE_SUBSTRINGS` entries
- [ ] `R2dbcAdminAuditRepository.publish()` writes only redacted metadata
- [ ] Unit tests cover redaction edge cases and confirm no sensitive keys leak
- [ ] Critical integration test: metadata with sensitive keys is published and stored without those keys
- [ ] `administrative/` context fully deleted (no remaining references)
- [ ] `administrative_audit_events` table dropped via Liquibase
- [ ] `backend-test-fast` passes
- [ ] Existing 6 handlers continue to emit `AdminAuditEvent` without modification
- [ ] Compilation clean — no pre-existing failures excluded from the success criteria
