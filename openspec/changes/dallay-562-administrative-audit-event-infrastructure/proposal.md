# Proposal: Administrative Audit Event Infrastructure

## Intent

Implement the reusable audit event model and persistence infrastructure needed to record Back Office administrative mutations in a safe, queryable form. The goal is to close the gap between capability handlers that perform administrative actions and the audit log—without ever leaking tokens, secrets, or sensitive payloads into stored audit records.

This is foundational infrastructure for later audit queries and compliance reporting. It does not build the audit UI.

## Scope

### In Scope
- Define the `AdministrativeAuditEvent` domain model (actor, action, target, timestamp, correlation ID, safe metadata)
- Establish the redaction policy: explicitly exclude raw tokens, passwords, secrets, and sensitive notification payloads
- Add a `AdministrativeAuditEventRepository` port in `domain/` and an R2DBC implementation in `infrastructure/persistence/`
- Provide an `AuditEventPublisher` service that capability handlers use to emit audit facts
- Unit tests validating persistence and redaction rules
- Liquibase migration for the `administrative_audit_events` table

### Out of Scope
- Admin audit query API or UI
- Arbitrary free-text notes stored in audit events
- Storing raw invitation tokens, reset tokens, refresh tokens, access tokens, passwords, or secrets
- MCP tool audit (already implemented in `mcp-tool-audit`)

## Capabilities

### New Capabilities
- `administrative-audit-event`: Core audit event persistence capability. Emits `AdministrativeAuditEvent` records on completed Back Office actions. Supports actor (principal ID + type), action (string), target (resource ID + type), timestamp, correlation ID, and a metadata map pre-scrubbed of sensitive values.

### Modified Capabilities
- None.

## Approach

1. **Domain model** — `AdministrativeAuditEvent` as a value object / entity in `server/smp/src/main/kotlin/com/profiletailors/smp/administrative/domain/`. Fields: `id`, `actorId`, `actorType`, `action`, `targetId`, `targetType`, `correlationId`, `metadata` (Map<String, String>), `occurredAt`. Metadata is the only extensible field; it is populated by callers using a pre-sanitized builder.

2. **Redaction policy** — A `SensitiveFieldRedactor` utility function that accepts a map and returns a new map with known sensitive keys removed or redacted: `password`, `token`, `secret`, `credential`, `key`, `invitationToken`, `resetToken`, `refreshToken`, `accessToken`. Any key whose lowercase name contains these substrings is excluded. Unit-tested against a known-bad set.

3. **Port** — `AdministrativeAuditEventRepository` interface in `domain/` (no Spring annotations). Implemented by `R2dbcAdministrativeAuditEventRepository` in `infrastructure/persistence/`.

4. **Publisher** — `AuditEventPublisher` in `application/` that accepts an `AdministrativeAuditEvent` and delegates to the repository. Capability handlers call this; they are responsible for building safe metadata before calling publish.

5. **Liquibase migration** — `db/migration/V<next>__create_administrative_audit_events.sql` using the existing `changelog.xml` pattern. Table: `administrative_audit_events` with UUID primary key, indexed on `actor_id`, `target_id`, `action`, `occurred_at`, and `correlation_id`.

6. **Tests** — Pure unit tests for `SensitiveFieldRedactor` and `AdministrativeAuditEvent` invariants. Repository tests using `WebTestClient` and the existing `BddDatabaseSupport` fixture pattern.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/administrative/` | New | New bounded context for administrative audit events |
| `server/smp/src/main/kotlin/com/profiletailors/smp/administrative/domain/` | New | Domain model and repository port |
| `server/smp/src/main/kotlin/com/profiletailors/smp/administrative/application/` | New | `AuditEventPublisher` service |
| `server/smp/src/main/kotlin/com/profiletailors/smp/administrative/infrastructure/` | New | R2DBC repository implementation |
| `server/smp/src/main/resources/db/migration/` | Modified | Liquibase migration for `administrative_audit_events` table |
| `openspec/specs/administrative-audit-event/` | New | Capability spec (sdd-spec phase) |
| `openspec/changes/dallay-562-administrative-audit-event-infrastructure/` | New | Change artifacts |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Missed sensitive field leaks into metadata | Low | Explicit denylist in `SensitiveFieldRedactor` with unit-test coverage; review metadata construction at each caller |
| Backwards-incompatible schema change later | Low | Audit table is append-only; no migration of existing rows needed |
| Low adoption if capability handlers don't call publisher | Medium | Document usage pattern; add integration test that verifies emission from a real handler |

## Rollback Plan

1. Revert the Liquibase migration by rolling back the `V<next>` changelog entry in `databasechangelog`.
2. Delete `administrative/` context packages.
3. Re-run `backend-test-fast` to confirm clean revert.
No data migration is needed since this is a new table with no production data at this stage.

## Dependencies

- None. This change introduces no new external dependencies. It uses existing R2DBC, Liquibase, and Spring Modulith patterns already established in the SMP backend.

## Success Criteria

- [ ] `AdministrativeAuditEvent` entity is persisted via `R2dbcAdministrativeAuditEventRepository`
- [ ] `SensitiveFieldRedactor` excludes all keys matching `password`, `token`, `secret`, `credential`, `key`, `invitationToken`, `resetToken`, `refreshToken`, `accessToken` (case-insensitive substring match)
- [ ] `AuditEventPublisher` is callable from capability handlers and persists a complete event
- [ ] Liquibase migration creates `administrative_audit_events` table with indexed columns
- [ ] Unit tests cover redaction edge cases and entity invariants
- [ ] `backend-test-fast` passes (pre-existing `BulkPublishingController` compilation errors are unrelated to this change; see evidence below)

## Evidence

### backend-test-fast output (pre-existing failure)

The existing `BulkPublishingController` has unresolved imports that cause compilation to fail. This is a pre-existing worktree issue unrelated to DALLAY-562:

```
Unresolved reference: BulkTemplateCsvResult, BulkTemplatesQuery, BulkTemplatesResult,
  GetBulkJobQuery, ScheduleBulkCommand, ScheduleBulkResult, ValidateBulkCommand,
  ValidateBulkResult, BulkImportJobRepository
```

The change directory and this proposal were created successfully. The audit infrastructure code (to be written in sdd-spec + sdd-apply) does not touch `BulkPublishingController`.

**Recommendation**: Fix `BulkPublishingController` as a separate change before merging this one, or accept that the backend gate will remain red until that file is resolved.
