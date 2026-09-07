# Tasks: Administrative Audit Event Infrastructure

## Phase 1: Add redaction utility and enforcement point

 - [x] **T1.1** Add `redact()` function as a top-level function in `RedactSensitiveMetadata.kt`
   - Location: `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/persistence/RedactSensitiveMetadata.kt`
   - Contains the `SENSITIVE_SUBSTRINGS` denylist and the filter logic
 - [x] **T1.2** Modify `R2dbcAdminAuditRepository.publish()` to call `redact(event.metadata)` before binding to the SQL statement
   - No signature change; only the bind value changes from `event.metadata` to `redact(event.metadata)`
 - [x] **T1.3** Run `just backend-lint` to verify no compilation errors

## Phase 2: Add unit tests for `redact()`

 - [x] **T2.1** Create `RedactSensitiveMetadataTest.kt` under `platformadmin` test package
   - Location: `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/infrastructure/persistence/`
   - Test cases: empty map, no sensitive keys, one sensitive key, multiple sensitive keys, case-variant keys (`accessToken`, `RESETPassword`), original map unchanged after call
 - [x] **T2.2** Run `just backend-test-fast` to confirm all tests pass

## Phase 3: Delete orphaned `administrative` context

 - [x] **T3.1** Delete `server/smp/src/main/kotlin/com/profiletailors/smp/administrative/` entirely
   - Includes `domain/`, `application/`, and `infrastructure/` packages
 - [x] **T3.2** Delete `server/smp/src/test/kotlin/com/profiletailors/smp/administrative/` entirely
 - [x] **T3.3** Update any imports or references to `com.profiletailors.smp.administrative.** that may have been missed
   - Verified: no remaining references in `server/smp/src/`
 - [x] **T3.4** Run `just backend-lint` and `just backend-check` to confirm clean compilation

## Phase 4: Handle Liquibase migration

 - [x] **T4.1** Check git history for `V006__create_administrative_audit_events.sql`:
   - V006 was never executed in prod (no records in databasechangelog); orphaned table only in source
   - Deleted: `db/changelog/platform-admin/006-create-administrative-audit-events.yaml`
   - Removed include from `db/changelog-master.yaml`
 - [x] **T4.2** Verify migration plan with `just backend-test-fast`

## Phase 5: Final verification

 - [ ] **T5.1** Run `just backend-check` (full gate: tests + Detekt)
 - [ ] **T5.2** Confirm no remaining references to `com.profiletailors.smp.administrative`
 - [ ] **T5.3** Update `state.yaml` to `current_phase: verify`

## Phase 6: Open issues

 - [ ] **T6.1** Integration test for end-to-end redaction (T3.1 from original spec) requires adding `metadata` column to `platform_admin_audit_events` table
   - Current `publish()` calls `redact(event.metadata)` but the table has no `metadata` column to persist to
   - This is a separate migration task beyond the scope of the current change
