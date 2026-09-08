# Design: Administrative Audit Event Infrastructure

## Technical Approach

Implement a new `administrative/` bounded context that provides a reusable audit event model and R2DBC persistence layer for recording Back Office administrative mutations. The approach follows hexagonal architecture: domain (entity + port) → application (publisher service) → infrastructure (R2DBC adapter). The bounded context is new and isolated; it introduces no external dependencies beyond existing R2DBC, Liquibase, and Spring Modulith infrastructure already present in the SMP backend.

## Architecture Decisions

### Decision: Package structure and bounded context marker

**Choice**: `com.profiletailors.smp.administrative` with a marker object `AdministrativeBoundedContext` in the root package, mirroring the `platformadmin/` context pattern.

**Alternatives considered**: Placing the audit event under `governance/` (existing context). Rejected because audit events are a distinct domain concept that does not belong to the compliance/governance bounded context; the audit table is append-only and has different access patterns from governance takedown/consent records.

**Rationale**: A dedicated bounded context follows the existing DDD structure of the SMP backend and keeps the audit model independent from governance invariants.

### Decision: `AdministrativeAuditEvent` as a plain data class, not an aggregate

**Choice**: `AdministrativeAuditEvent` is a plain Kotlin data class with validation in an `init` block, not an `@AggregateRoot`.

**Alternatives considered**: Modeling it as an `@AggregateRoot` with `@AggregateRootId`. Rejected because audit events are immutable once written; they have no behavior, no state transitions, and no invariants beyond field validity. The repository directly persists the data class without a domain service.

**Rationale**: Matches the simplicity of the use case; avoids the ceremony of aggregate modeling for a write-once entity.

### Decision: `SensitiveFieldRedactor` as a top-level function returning a new map

**Choice**: `SensitiveFieldRedactor` is a public standalone function `redact(metadata: Map<String, String>): Map<String, String>`.

**Alternatives considered**: A class with mutable state or a Spring component. Rejected because the redaction logic is pure and stateless; a function is simpler and trivially testable.

**Rationale**: The denylist is a static set of substring patterns; no instance state is needed.

## Data Flow

```
Capability Handler
    │
    ├── builds safe metadata (calls SensitiveFieldRedactor.redact())
    └── calls AuditEventPublisher.publish(event)
              │
              └── delegates to AdministrativeAuditEventRepository.save()
                        │
                        └── R2dbcAdministrativeAuditEventRepository.save()
                                  │
                                  └── INSERT INTO administrative_audit_events (...)
```

## Package Structure

```
server/smp/src/main/kotlin/com/profiletailors/smp/administrative/
├── AdministrativeBoundedContext.kt          # marker object
├── domain/
│   ├── AdministrativeAuditEvent.kt           # entity + SensitiveFieldRedactor
│   └── AdministrativeAuditEventRepository.kt  # port interface
└── infrastructure/
    └── persistence/
        └── R2dbcAdministrativeAuditEventRepository.kt
```

```
server/smp/src/main/resources/db/changelog/platform-admin/
└── 006-create-administrative-audit-events.yaml
```

## Interfaces / Contracts

### `AdministrativeAuditEvent` entity

```kotlin
package com.profiletailors.smp.administrative.domain

import java.time.Instant
import java.util.UUID

data class AdministrativeAuditEvent(
    val id: UUID,
    val actorId: UUID,
    val actorType: String,
    val action: String,
    val targetId: String,
    val targetType: String,
    val correlationId: String?,
    val metadata: Map<String, String>,
    val occurredAt: Instant,
) {
    init {
        require(actorType.isNotBlank()) { "actorType must not be blank" }
        require(action.isNotBlank()) { "action must not be blank" }
        require(targetType.isNotBlank()) { "targetType must not be blank" }
        require(targetId.isNotBlank()) { "targetId must not be blank" }
        require(metadata.keys.none { SENSITIVE_SUBSTRINGS.any { s -> it.lowercase().contains(s) } }) {
            "metadata must not contain sensitive keys"
        }
    }

    companion object {
        private val SENSITIVE_SUBSTRINGS = listOf(
            "password", "token", "secret", "credential", "key",
            "invitationtoken", "resettoken", "refreshtoken", "accesstoken",
        )
    }
}

fun redact(metadata: Map<String, String>): Map<String, String> =
    metadata.filterKeys { key ->
        SENSITIVE_SUBSTRINGS.none { substring -> key.lowercase().contains(substring) }
    }
```

### `SensitiveFieldRedactor` — exact implementation

Case-insensitive substring match: any map key whose lowercase form contains any of the denylist substrings is excluded. The function is defined as a top-level function alongside `AdministrativeAuditEvent` in `domain/AdministrativeAuditEvent.kt`.

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

### `AdministrativeAuditEventRepository` — port interface (in `domain/`)

```kotlin
package com.profiletailors.smp.administrative.domain

interface AdministrativeAuditEventRepository {
    suspend fun save(event: AdministrativeAuditEvent)
}
```

No Spring annotations. Pure interface in the domain layer.

### `R2dbcAdministrativeAuditEventRepository` — infrastructure adapter

Pattern mirrors `R2dbcInvitationRepository`: constructor-injected `DatabaseClient`, suspend functions, `awaitSingle`/`awaitSingleOrNull`, `bind`/`bindNullableInstant` extension functions.

```kotlin
package com.profiletailors.smp.administrative.infrastructure.persistence

import com.profiletailors.smp.administrative.domain.AdministrativeAuditEvent
import com.profiletailors.smp.administrative.domain.AdministrativeAuditEventRepository
import io.r2dbc.spi.Readable
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class R2dbcAdministrativeAuditEventRepository(
    private val databaseClient: DatabaseClient,
) : AdministrativeAuditEventRepository {

    override suspend fun save(event: AdministrativeAuditEvent) {
        databaseClient.sql(INSERT)
            .bind("id", event.id)
            .bind("actorId", event.actorId)
            .bind("actorType", event.actorType)
            .bind("action", event.action)
            .bind("targetId", event.targetId)
            .bind("targetType", event.targetType)
            .bindNullableString("correlationId", event.correlationId)
            .bind("metadata", event.metadata)
            .bind("occurredAt", OffsetDateTime.ofInstant(event.occurredAt, ZoneOffset.UTC))
            .then()
            .awaitSingle()
    }

    private fun Readable.toEvent(): AdministrativeAuditEvent = AdministrativeAuditEvent(
        id = requireNotNull(get("id", UUID::class.java)),
        actorId = requireNotNull(get("actor_id", UUID::class.java)),
        actorType = requireNotNull(get("actor_type", String::class.java)),
        action = requireNotNull(get("action", String::class.java)),
        targetId = requireNotNull(get("target_id", String::class.java)),
        targetType = requireNotNull(get("target_type", String::class.java)),
        correlationId = get("correlation_id", String::class.java),
        metadata = requireNotNull(get("metadata", Map::class.java)) as Map<String, String>,
        occurredAt = requireNotNull(get("occurred_at", OffsetDateTime::class.java)).toInstant(),
    )

    companion object {
        private const val COLUMNS = """
            id, actor_id, actor_type, action, target_id, target_type,
            correlation_id, metadata, occurred_at
        """
        private const val INSERT = """
            INSERT INTO administrative_audit_events (
                id, actor_id, actor_type, action, target_id, target_type,
                correlation_id, metadata, occurred_at
            ) VALUES (
                :id, :actorId, :actorType, :action, :targetId, :targetType,
                :correlationId, :metadata, :occurredAt
            )
        """
    }
}

private fun DatabaseClient.GenericExecuteSpec.bindNullableString(
    name: String,
    value: String?,
): DatabaseClient.GenericExecuteSpec =
    if (value != null) bind(name, value) else bindNull(name, String::class.java)
```

### `AuditEventPublisher` — application service

```kotlin
package com.profiletailors.smp.administrative.application

import com.profiletailors.smp.administrative.domain.AdministrativeAuditEvent
import com.profiletailors.smp.administrative.domain.AdministrativeAuditEventRepository

class AuditEventPublisher(
    private val repository: AdministrativeAuditEventRepository,
) {
    suspend fun publish(event: AdministrativeAuditEvent) {
        repository.save(event)
    }
}
```

## Liquibase Migration

File: `server/smp/src/main/resources/db/changelog/platform-admin/006-create-administrative-audit-events.yaml`

Columns: `id (uuid PK)`, `actor_id (uuid)`, `actor_type (varchar 64)`, `action (varchar 64)`, `target_id (varchar 255)`, `target_type (varchar 64)`, `correlation_id (varchar 128 nullable)`, `metadata (text or jsonb)`, `occurred_at (timestamptz)`.

Indexes on: `actor_id`, `target_id`, `action`, `occurred_at`, `correlation_id`.

```yaml
databaseChangeLog:
  - changeSet:
      id: platform-admin-006-create-administrative-audit-events
      author: administrative
      changes:
        - createTable:
            tableName: administrative_audit_events
            columns:
              - column:
                  name: id
                  type: uuid
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: actor_id
                  type: uuid
                  constraints:
                    nullable: false
              - column:
                  name: actor_type
                  type: varchar(64)
                  constraints:
                    nullable: false
              - column:
                  name: action
                  type: varchar(64)
                  constraints:
                    nullable: false
              - column:
                  name: target_id
                  type: varchar(255)
                  constraints:
                    nullable: false
              - column:
                  name: target_type
                  type: varchar(64)
                  constraints:
                    nullable: false
              - column:
                  name: correlation_id
                  type: varchar(128)
              - column:
                  name: metadata
                  type: text
              - column:
                  name: occurred_at
                  type: timestamp with time zone
                  constraints:
                    nullable: false
        - createIndex:
            tableName: administrative_audit_events
            indexName: idx_administrative_audit_actor
            columns:
              - column:
                  name: actor_id
        - createIndex:
            tableName: administrative_audit_events
            indexName: idx_administrative_audit_target
            columns:
              - column:
                  name: target_id
        - createIndex:
            tableName: administrative_audit_events
            indexName: idx_administrative_audit_action
            columns:
              - column:
                  name: action
        - createIndex:
            tableName: administrative_audit_events
            indexName: idx_administrative_audit_occurred_at
            columns:
              - column:
                  name: occurred_at
        - createIndex:
            tableName: administrative_audit_events
            indexName: idx_administrative_audit_correlation
            columns:
              - column:
                  name: correlation_id
```

Add to `db.changelog-master.yaml`:
```yaml
  - include:
      file: db/changelog/platform-admin/006-create-administrative-audit-events.yaml
```

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Unit | `SensitiveFieldRedactor` edge cases (keys with/without sensitive substrings, case sensitivity, empty map) | Plain JUnit test, no Spring context |
| Unit | `AdministrativeAuditEvent` invariants in `init` block | JUnit `assertThrows` for invalid inputs |
| Unit | `AuditEventPublisher` delegation | Mock `AdministrativeAuditEventRepository`, verify `save` is called with correct event |
| Integration | `R2dbcAdministrativeAuditEventRepository` round-trip | `BddDatabaseSupport` + `DatabaseClient`; seed a row, reload by id, assert fields match |

Integration test follows `R2dbcInvitationRepositoryTest` pattern: inject `DatabaseClient` via `@BeforeEach`, call `repository.save()`, then `findById()` and assert equality.

`BddDatabaseSupport` cleanup list (`cleanupStatements()`) will need:
```kotlin
"DELETE FROM administrative_audit_events",
```

## Migration / Rollback

Rollback: remove the `include` entry from `db.changelog-master.yaml` and drop the `administrative_audit_events` table. No data migration needed at this stage — the table is new.

## Open Questions

- [ ] `metadata` column type: `text` (JSON string) or native `jsonb`? JSON string is simpler and matches how other text maps are stored; `jsonb` enables JSON path queries but requires casting on read. Recommend `text` for now, JSON-serialized by the application layer.
- [ ] `actorType` values — should these be an enum or freeform strings? Proposal uses freeform `String`; if a fixed set of actor types emerges, extract a `@ValueObject enum class ActorType`.
