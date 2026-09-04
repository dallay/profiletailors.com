# Tasks: Administrative Audit Event Infrastructure

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~450–550 |
| 400-line budget risk | Medium |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | ask-on-risk |
| Chain strategy | single-pr |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: single-pr
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Full implementation | PR 1 | Single PR for all tasks below |

---

## Phase 1: Unit Tests — Redaction (TDD RED)

### Task 1.1 — Write `SensitiveFieldRedactorTest` ✅

**Status**: Implemented
**Files created**: `server/smp/src/test/kotlin/com/profiletailors/smp/administrative/domain/SensitiveFieldRedactorTest.kt`

**Description**: Write `SensitiveFieldRedactorTest` in `server/smp/src/test/kotlin/com/profiletailors/smp/administrative/domain/` covering:
- Exact sensitive keys: `password`, `token`, `secret`, `credential`, `key`
- Compound/camelCase variants: `invitationToken`, `resetToken`, `refreshToken`, `accessToken`, `userToken`
- Case-insensitive matching (`PASSWORD`, `MyToken`)
- Null input returns empty map
- Empty map returns empty map
- Map with no sensitive keys returns identical entries
- Map with mixed sensitive and non-sensitive keys

**Verification command**: `./gradlew :server:smp:test --tests "com.profiletailors.smp.administrative.domain.SensitiveFieldRedactorTest" --info 2>&1 | tail -20`

**Affected files** (create):
- `server/smp/src/test/kotlin/com/profiletailors/smp/administrative/domain/SensitiveFieldRedactorTest.kt`

**Type**: test

---

### Task 1.2 — Write `AdministrativeAuditEventTest` ✅

**Status**: Implemented
**Files created**: `server/smp/src/test/kotlin/com/profiletailors/smp/administrative/domain/AdministrativeAuditEventTest.kt`

**Description**: Write `AdministrativeAuditEventTest` covering:
- Construction with valid required fields + null optional succeeds
- Construction with blank `actorType` throws `IllegalArgumentException`
- Construction with blank `action` throws `IllegalArgumentException`
- Construction with blank `targetId` throws `IllegalArgumentException`
- Construction with blank `targetType` throws `IllegalArgumentException`
- Construction with sensitive key in metadata throws `IllegalArgumentException`

**Verification command**: `./gradlew :server:smp:test --tests "com.profiletailors.smp.administrative.domain.AdministrativeAuditEventTest" --info 2>&1 | tail -20`

**Affected files** (create):
- `server/smp/src/test/kotlin/com/profiletailors/smp/administrative/domain/AdministrativeAuditEventTest.kt`

**Type**: test

---

## Phase 2: Domain Model

### Task 2.1 — Create `AdministrativeBoundedContext` marker ✅

**Status**: Implemented
**Files created**: `server/smp/src/main/kotlin/com/profiletailors/smp/administrative/AdministrativeBoundedContext.kt`

**Description**: Create marker object `AdministrativeBoundedContext` in `com.profiletailors.smp.administrative` root package.

**Verification command**: `./gradlew :server:smp:compileKotlin 2>&1 | grep -E "(AdministrativeBoundedContext|BUILD)"`

**Affected files** (create):
- `server/smp/src/main/kotlin/com/profiletailors/smp/administrative/AdministrativeBoundedContext.kt`

**Type**: implementation

---

### Task 2.2 — Create `AdministrativeAuditEvent` entity ✅

**Status**: Implemented
**Files created**: `server/smp/src/main/kotlin/com/profiletailors/smp/administrative/domain/AdministrativeAuditEvent.kt`

**Description**: Create `AdministrativeAuditEvent.kt` data class in `com.profiletailors.smp.administrative.domain` with:
- All fields per spec: `id (UUID)`, `actorId (UUID)`, `actorType (String)`, `action (String)`, `targetId (String)`, `targetType (String)`, `correlationId (String?)`, `metadata (Map<String, String>)`, `occurredAt (Instant)`
- `init` block validating non-blank required fields
- `SENSITIVE_SUBSTRINGS` denylist: `password`, `token`, `secret`, `credential`, `key`, `invitationtoken`, `resettoken`, `refreshtoken`, `accesstoken`
- `metadata` init check: reject if any key lowercase contains a denylist substring
- Top-level `redact()` function: pure, case-insensitive, null-safe → returns filtered map

**Verification command**: `./gradlew :server:smp:compileKotlin 2>&1 | grep -E "(AdministrativeAuditEvent|BUILD)"`

**Affected files** (create):
- `server/smp/src/main/kotlin/com/profiletailors/smp/administrative/domain/AdministrativeAuditEvent.kt`

**Type**: implementation

---

## Phase 3: Port Interface

### Task 3.1 — Create `AdministrativeAuditEventRepository` port ✅

**Status**: Implemented
**Files created**: `server/smp/src/main/kotlin/com/profiletailors/smp/administrative/domain/AdministrativeAuditEventRepository.kt`

**Description**: Create `AdministrativeAuditEventRepository.kt` interface in `com.profiletailors.smp.administrative.domain` with:
- `suspend fun save(event: AdministrativeAuditEvent): AdministrativeAuditEvent`
- `suspend fun findById(id: UUID): AdministrativeAuditEvent?`
- `suspend fun findByActor(actorId: UUID): List<AdministrativeAuditEvent>`
- `suspend fun findByTarget(targetType: String, targetId: String): List<AdministrativeAuditEvent>`
- `suspend fun findByCorrelationId(correlationId: String): List<AdministrativeAuditEvent>`
- No Spring annotations

**Verification command**: `./gradlew :server:smp:compileKotlin 2>&1 | grep -E "(AdministrativeAuditEventRepository|BUILD)"`

**Affected files** (create):
- `server/smp/src/main/kotlin/com/profiletailors/smp/administrative/domain/AdministrativeAuditEventRepository.kt`

**Type**: implementation

---

## Phase 4: Application Service

### Task 4.1 — Create `AuditEventPublisher` ✅

**Status**: Implemented
**Files created**: `server/smp/src/main/kotlin/com/profiletailors/smp/administrative/application/AuditEventPublisher.kt`

**Description**: Create `AuditEventPublisher` in `com.profiletailors.smp.administrative.application` with constructor-injected `AdministrativeAuditEventRepository`. `publish()` delegates to `repository.save()`. Does NOT call `redact()` — caller is responsible for pre-sanitization.

**Verification command**: `./gradlew :server:smp:compileKotlin 2>&1 | grep -E "(AuditEventPublisher|BUILD)"`

**Affected files** (create):
- `server/smp/src/main/kotlin/com/profiletailors/smp/administrative/application/AuditEventPublisher.kt`

**Type**: implementation

---

### Task 4.2 — Write `AuditEventPublisherTest` ✅

**Status**: Implemented
**Files created**: `server/smp/src/test/kotlin/com/profiletailors/smp/administrative/application/AuditEventPublisherTest.kt`

**Description**: Write `AuditEventPublisherTest` using a mock `AdministrativeAuditEventRepository`. Verify `publish()` calls `repository.save()` with the correct event.

**Verification command**: `./gradlew :server:smp:test --tests "com.profiletailors.smp.administrative.application.AuditEventPublisherTest" --info 2>&1 | tail -20`

**Affected files** (create):
- `server/smp/src/test/kotlin/com/profiletailors/smp/administrative/application/AuditEventPublisherTest.kt`

**Type**: test

---

## Phase 5: Infrastructure — R2DBC Repository

### Task 5.1 — Create `R2dbcAdministrativeAuditEventRepository` ✅

**Status**: Implemented
**Files created**: `server/smp/src/main/kotlin/com/profiletailors/smp/administrative/infrastructure/persistence/R2dbcAdministrativeAuditEventRepository.kt`

**Description**: Create `R2dbcAdministrativeAuditEventRepository.kt` in `com.profiletailors.smp.administrative.infrastructure.persistence`:
- `@Repository` annotated, constructor-injected `DatabaseClient`
- Implement all five port methods using `awaitSingle`/`awaitSingleOrNull`
- `save()`: INSERT with all fields, `bind`/`bindNullableString` for optional `correlationId`
- `findById()`: SELECT by id → `toEvent()` mapper
- `findByActor()`: SELECT WHERE actor_id = :actorId ORDER BY occurred_at DESC
- `findByTarget()`: SELECT WHERE target_type = :targetType AND target_id = :targetId ORDER BY occurred_at DESC
- `findByCorrelationId()`: SELECT WHERE correlation_id = :correlationId ORDER BY occurred_at DESC
- `toEvent()` private extension on `Readable`
- JSON-serialize `metadata` map on save; parse on read
- Use `OffsetDateTime.ofInstant(event.occurredAt, ZoneOffset.UTC)` for `occurredAt` binding

**Verification command**: `./gradlew :server:smp:compileKotlin 2>&1 | grep -E "(R2dbcAdministrativeAuditEventRepository|BUILD)"`

**Affected files** (create):
- `server/smp/src/main/kotlin/com/profiletailors/smp/administrative/infrastructure/persistence/R2dbcAdministrativeAuditEventRepository.kt`

**Type**: implementation

---

### Task 5.2 — Write repository integration test

**Description**: Create `R2dbcAdministrativeAuditEventRepositoryTest` using `BddDatabaseSupport`:
- Inject `DatabaseClient` in `@BeforeEach`
- Add `DELETE FROM administrative_audit_events` to cleanupStatements
- Test `save()` + `findById()` round-trip: assert all fields match
- Test `findByActor()`: seed 2 events, query by correct actorId → returns 2
- Test `findByActor()` with unknown id → returns empty list
- Test `findByTarget()` and `findByCorrelationId()` queries
- Use `R2dbcInvitationRepositoryTest` as structural reference

**Verification command**: `./gradlew :server:smp:test --tests "com.profiletailors.smp.administrative.infrastructure.persistence.R2dbcAdministrativeAuditEventRepositoryTest" --info 2>&1 | tail -30`

**Affected files** (create):
- `server/smp/src/test/kotlin/com/profiletailors/smp/administrative/infrastructure/persistence/R2dbcAdministrativeAuditEventRepositoryTest.kt`

**Type**: test

---

## Phase 6: Liquibase Migration

### Task 6.1 — Create `V006__create_administrative_audit_events.yaml` ✅

**Status**: Implemented
**Files created**: `server/smp/src/main/resources/db/changelog/platform-admin/006-create-administrative-audit-events.yaml`

**Description**: Create migration file at `server/smp/src/main/resources/db/changelog/platform-admin/006-create-administrative-audit-events.yaml`:
- Create table `administrative_audit_events` with columns: `id (uuid PK NOT NULL)`, `actor_id (uuid NOT NULL)`, `actor_type (varchar(64) NOT NULL)`, `action (varchar(128) NOT NULL)`, `target_id (varchar(255) NOT NULL)`, `target_type (varchar(64) NOT NULL)`, `correlation_id (varchar(128) NULL)`, `metadata (text NULL)`, `occurred_at (timestamptz NOT NULL)`
- Indexes: `idx_administrative_audit_actor` on `actor_id`, `idx_administrative_audit_target` on `target_id`, `idx_administrative_audit_action` on `action`, `idx_administrative_audit_occurred_at` on `occurred_at`, `idx_administrative_audit_correlation` on `correlation_id`

**Verification command**: `./gradlew :server:smp:compileKotlin 2>&1 && echo "Migration compiled OK"`

**Affected files** (create):
- `server/smp/src/main/resources/db/changelog/platform-admin/006-create-administrative-audit-events.yaml`

**Type**: implementation

---

### Task 6.2 — Add migration to changelog master ✅

**Status**: Implemented
**Files modified**: `server/smp/src/main/resources/db/changelog-master.yaml`

**Description**: Add `include` entry to `db.changelog-master.yaml` for `006-create-administrative-audit-events.yaml`.

**Verification command**: `grep -q "006-create-administrative-audit-events" server/smp/src/main/resources/db/changelog-master.yaml && echo "Entry found" || echo "MISSING"`

**Affected files** (modify):
- `server/smp/src/main/resources/db/changelog-master.yaml`

**Type**: implementation

---

## Phase 7: Final Verification

### Task 7.1 — Run full backend test suite

**Description**: Run `just backend-check` to execute all Kotlin unit/integration tests, Detekt, and compilation checks for the SMP backend.

**Verification command**: `just backend-check 2>&1 | tail -40`

**Affected files**: All above.

**Type**: verification

---

## Implementation Order Rationale

1. **Tests first (RED)**: Writing `SensitiveFieldRedactorTest` and `AdministrativeAuditEventTest` before any implementation enforces TDD discipline and defines expected behavior clearly.
2. **Domain entity**: `AdministrativeAuditEvent` is the core — no other layer depends on it being fully implemented yet, so it comes next.
3. **Port interface**: The repository interface defines the contract; infrastructure depends on it but not vice versa.
4. **Application service**: `AuditEventPublisher` depends on the port; written after the port so the dependency is satisfied.
5. **Infrastructure**: `R2dbcAdministrativeAuditEventRepository` implements the port and is the most complex piece; integration tests follow it.
6. **Migration**: Must be present before integration tests can use a real database, and before the infrastructure compiles in a real run.
7. **Full suite**: Final `backend-check` verifies no regressions across the entire change.
