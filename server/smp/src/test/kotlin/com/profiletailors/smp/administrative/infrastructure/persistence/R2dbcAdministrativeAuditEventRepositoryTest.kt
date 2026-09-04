package com.profiletailors.smp.administrative.infrastructure.persistence

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.profiletailors.smp.administrative.domain.AdministrativeAuditEvent
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcAdministrativeAuditEventRepositoryTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    private val objectMapper = jacksonObjectMapper()
    private val repository by lazy { R2dbcAdministrativeAuditEventRepository(databaseClient, objectMapper) }

    @Test
    fun `saves and finds event by id`() = runTest {
        val event = createEvent(
            id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            correlationId = "corr-123",
            metadata = mapOf("key1" to "val1", "key2" to "val2"),
        )

        val saved = repository.save(event)
        assertEquals(event, saved)

        val found = repository.findById(event.id)
        assertNotNull(found)
        assertEquals(event.id, found.id)
        assertEquals(event.actorId, found.actorId)
        assertEquals(event.actorType, found.actorType)
        assertEquals(event.action, found.action)
        assertEquals(event.targetId, found.targetId)
        assertEquals(event.targetType, found.targetType)
        assertEquals(event.correlationId, found.correlationId)
        assertEquals(event.metadata, found.metadata)
        assertEquals(event.occurredAt, found.occurredAt)
    }

    @Test
    fun `findById returns null when event does not exist`() = runTest {
        val found = repository.findById(UUID.fromString("99999999-9999-9999-9999-999999999999"))
        assertNull(found)
    }

    @Test
    fun `handles null correlationId and empty metadata`() = runTest {
        val event = createEvent(
            id = UUID.fromString("22222222-2222-2222-2222-222222222222"),
            correlationId = null,
            metadata = emptyMap(),
        )

        repository.save(event)

        val found = repository.findById(event.id)
        assertNotNull(found)
        assertNull(found.correlationId)
        assertEquals(emptyMap(), found.metadata)
    }

    @Test
    fun `findByActor returns events ordered by occurredAt descending`() = runTest {
        val actorId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val older = createEvent(
            id = UUID.fromString("33333333-3333-3333-3333-333333333301"),
            actorId = actorId,
            occurredAt = Instant.parse("2026-08-01T10:00:00Z"),
        )
        val newer = createEvent(
            id = UUID.fromString("33333333-3333-3333-3333-333333333302"),
            actorId = actorId,
            occurredAt = Instant.parse("2026-08-02T10:00:00Z"),
        )
        val otherActorEvent = createEvent(
            id = UUID.fromString("33333333-3333-3333-3333-333333333303"),
            actorId = UUID.randomUUID(),
        )

        repository.save(older)
        repository.save(newer)
        repository.save(otherActorEvent)

        val events = repository.findByActor(actorId)
        assertEquals(2, events.size)
        assertEquals(newer.id, events[0].id)
        assertEquals(older.id, events[1].id)
    }

    @Test
    fun `findByTarget returns events matching targetType and targetId ordered by occurredAt descending`() = runTest {
        val targetType = "WAITLIST_ENTRY"
        val targetId = "entry-100"
        val older = createEvent(
            id = UUID.fromString("44444444-4444-4444-4444-444444444401"),
            targetType = targetType,
            targetId = targetId,
            occurredAt = Instant.parse("2026-08-01T10:00:00Z"),
        )
        val newer = createEvent(
            id = UUID.fromString("44444444-4444-4444-4444-444444444402"),
            targetType = targetType,
            targetId = targetId,
            occurredAt = Instant.parse("2026-08-02T10:00:00Z"),
        )
        val mismatchedType = createEvent(
            id = UUID.fromString("44444444-4444-4444-4444-444444444403"),
            targetType = "OTHER_TARGET",
            targetId = targetId,
        )

        repository.save(older)
        repository.save(newer)
        repository.save(mismatchedType)

        val events = repository.findByTarget(targetType, targetId)
        assertEquals(2, events.size)
        assertEquals(newer.id, events[0].id)
        assertEquals(older.id, events[1].id)
    }

    @Test
    fun `findByCorrelationId returns events matching correlationId ordered by occurredAt descending`() = runTest {
        val correlationId = "corr-unique-99"
        val older = createEvent(
            id = UUID.fromString("55555555-5555-5555-5555-555555555501"),
            correlationId = correlationId,
            occurredAt = Instant.parse("2026-08-01T10:00:00Z"),
        )
        val newer = createEvent(
            id = UUID.fromString("55555555-5555-5555-5555-555555555502"),
            correlationId = correlationId,
            occurredAt = Instant.parse("2026-08-02T10:00:00Z"),
        )
        val otherCorrelation = createEvent(
            id = UUID.fromString("55555555-5555-5555-5555-555555555503"),
            correlationId = "corr-other",
        )

        repository.save(older)
        repository.save(newer)
        repository.save(otherCorrelation)

        val events = repository.findByCorrelationId(correlationId)
        assertEquals(2, events.size)
        assertEquals(newer.id, events[0].id)
        assertEquals(older.id, events[1].id)
    }

    @AfterEach
    fun cleanDatabase() = runTest {
        databaseClient.sql("DELETE FROM administrative_audit_events").fetch().rowsUpdated().awaitSingle()
    }

    private fun createEvent(
        id: UUID = UUID.randomUUID(),
        actorId: UUID = UUID.randomUUID(),
        actorType: String = "USER",
        action: String = "waitlist.invite",
        targetId: String = "target-1",
        targetType: String = "WAITLIST_ENTRY",
        correlationId: String? = "corr-1",
        metadata: Map<String, String> = mapOf("role" to "admin"),
        occurredAt: Instant = Instant.parse("2026-08-15T12:00:00Z"),
    ): AdministrativeAuditEvent = AdministrativeAuditEvent(
        id = id,
        actorId = actorId,
        actorType = actorType,
        action = action,
        targetId = targetId,
        targetType = targetType,
        correlationId = correlationId,
        metadata = metadata,
        occurredAt = occurredAt,
    )

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("administrative_audit_events_test")
    }
}
