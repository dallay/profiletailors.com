package com.profiletailors.smp.publishing.infrastructure.persistence

import com.profiletailors.smp.publishing.domain.RecurrenceFrequency
import com.profiletailors.smp.publishing.domain.RecurrenceRule
import com.profiletailors.smp.publishing.domain.RecurringSchedule
import com.profiletailors.smp.publishing.domain.RecurringScheduleStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.FetchSpec
import org.springframework.r2dbc.core.RowsFetchSpec
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID
import java.util.function.BiFunction

class R2dbcRecurringScheduleRepositoryTest {

    private val databaseClient = mockk<DatabaseClient>()
    private val repository = R2dbcRecurringScheduleRepository(databaseClient)
    private val fixedInstant = Instant.parse("2026-01-01T12:00:00Z")

    private fun sampleSchedule(
        id: String = SCHEDULE_ID,
        workspaceId: String = WORKSPACE_ID,
        templatePostId: String = TEMPLATE_ID,
    ) = RecurringSchedule(
        id = id,
        workspaceId = workspaceId,
        createdBy = PRINCIPAL_ID,
        templatePostId = templatePostId,
        recurrenceRule = RecurrenceRule(
            frequency = RecurrenceFrequency.WEEKLY,
            interval = 1,
            daysOfWeek = setOf(1, 3, 5),
            dayOfMonth = null,
            endDate = LocalDate.parse("2026-12-31"),
            maxOccurrences = 10,
        ),
        timezone = "UTC",
        nextScheduledAt = fixedInstant,
        status = RecurringScheduleStatus.ACTIVE,
        createdAt = fixedInstant,
        updatedAt = fixedInstant,
    )

    @Test
    fun `create binds all fields and inserts record`() = runTest {
        val schedule = sampleSchedule()
        val spec = mockWriteSpec()
        every { databaseClient.sql(ofType(String::class)) } returns spec

        val result = repository.create(schedule)

        assertEquals(schedule.id, result.id)
        verify { spec.bind("id", UUID.fromString(SCHEDULE_ID)) }
        verify { spec.bind("workspaceId", UUID.fromString(WORKSPACE_ID)) }
        verify { spec.bind("frequency", "WEEKLY") }
        verify { spec.bind("interval", 1) }
        verify { spec.bind("daysOfWeek", "1,3,5") }
        verify { spec.bind("timezone", "UTC") }
        verify { spec.bind("status", "ACTIVE") }
        verify { spec.bind("createdBy", UUID.fromString(PRINCIPAL_ID)) }
        verify { spec.bind("templatePostId", UUID.fromString(TEMPLATE_ID)) }
    }

    @Test
    fun `update binds fields and updates existing record`() = runTest {
        val schedule = sampleSchedule()
        val spec = mockWriteSpec(rowsUpdated = 1L)
        every { databaseClient.sql(ofType(String::class)) } returns spec

        val result = repository.update(schedule)

        assertEquals(schedule.id, result.id)
        verify { spec.bind("id", UUID.fromString(SCHEDULE_ID)) }
        verify { spec.bind("workspaceId", UUID.fromString(WORKSPACE_ID)) }
    }

    @Test
    fun `update throws exception when no rows updated`() = runTest {
        val schedule = sampleSchedule()
        val spec = mockWriteSpec(rowsUpdated = 0L)
        every { databaseClient.sql(ofType(String::class)) } returns spec

        assertThrows<IllegalArgumentException> {
            repository.update(schedule)
        }
    }

    @Test
    fun `findByWorkspaceAndId returns schedule when found`() = runTest {
        val genericSpec = mockk<DatabaseClient.GenericExecuteSpec>(relaxed = true)
        val rowsSpec = mockk<RowsFetchSpec<RecurringSchedule>>(relaxed = false)
        every { databaseClient.sql(ofType(String::class)) } returns genericSpec
        every { genericSpec.bind(any<String>(), any<Any>()) } returns genericSpec

        val slot = slot<BiFunction<Row, RowMetadata, RecurringSchedule>>()
        every { genericSpec.map(capture(slot)) } returns rowsSpec
        every { rowsSpec.all() } answers {
            val fn = slot.captured
            val row = mockReadableRow(SCHEDULE_ID, WORKSPACE_ID)
            Flux.just(fn.apply(row, mockk()))
        }

        val result = repository.findByWorkspaceAndId(WORKSPACE_ID, SCHEDULE_ID)

        assertNotNull(result)
        assertEquals(SCHEDULE_ID, result?.id)
        assertEquals(WORKSPACE_ID, result?.workspaceId)
    }

    @Test
    fun `findByWorkspaceAndId returns null when not found`() = runTest {
        val genericSpec = mockk<DatabaseClient.GenericExecuteSpec>(relaxed = true)
        val rowsSpec = mockk<RowsFetchSpec<RecurringSchedule>>(relaxed = false)
        every { databaseClient.sql(ofType(String::class)) } returns genericSpec
        every { genericSpec.bind(any<String>(), any<Any>()) } returns genericSpec

        val slot = slot<BiFunction<Row, RowMetadata, RecurringSchedule>>()
        every { genericSpec.map(capture(slot)) } returns rowsSpec
        every { rowsSpec.all() } returns Flux.empty()

        val result = repository.findByWorkspaceAndId(WORKSPACE_ID, UUID.randomUUID().toString())

        assertNull(result)
    }

    @Test
    fun `findByWorkspace returns list of active schedules`() = runTest {
        val genericSpec = mockk<DatabaseClient.GenericExecuteSpec>(relaxed = true)
        val rowsSpec = mockk<RowsFetchSpec<RecurringSchedule>>(relaxed = false)
        every { databaseClient.sql(ofType(String::class)) } returns genericSpec
        every { genericSpec.bind(any<String>(), any<Any>()) } returns genericSpec

        val slot = slot<BiFunction<Row, RowMetadata, RecurringSchedule>>()
        every { genericSpec.map(capture(slot)) } returns rowsSpec
        every { rowsSpec.all() } answers {
            val fn = slot.captured
            val row1 = mockReadableRow(SCHEDULE_ID, WORKSPACE_ID)
            val row2 = mockReadableRow(SECOND_SCHEDULE_ID, WORKSPACE_ID)
            Flux.just(fn.apply(row1, mockk()), fn.apply(row2, mockk()))
        }

        val result = repository.findByWorkspace(WORKSPACE_ID)

        assertEquals(2, result.size)
        assertEquals(SCHEDULE_ID, result[0].id)
        assertEquals(SECOND_SCHEDULE_ID, result[1].id)
    }

    @Test
    fun `pauseByTemplatePost executes update query`() = runTest {
        val spec = mockWriteSpec(rowsUpdated = 1L)
        every { databaseClient.sql(ofType(String::class)) } returns spec

        repository.pauseByTemplatePost(WORKSPACE_ID, TEMPLATE_ID)

        verify { spec.bind("paused", "PAUSED") }
        verify { spec.bind("workspaceId", UUID.fromString(WORKSPACE_ID)) }
        verify { spec.bind("templatePostId", UUID.fromString(TEMPLATE_ID)) }
        verify { spec.bind("active", "ACTIVE") }
    }

    @Test
    fun `delete returns true when rows updated`() = runTest {
        val spec = mockWriteSpec(rowsUpdated = 1L)
        every { databaseClient.sql(ofType(String::class)) } returns spec

        val deleted = repository.delete(WORKSPACE_ID, SCHEDULE_ID)

        assertTrue(deleted)
        verify { spec.bind("cancelled", "CANCELLED") }
        verify { spec.bind("existingCancelled", "CANCELLED") }
        verify { spec.bind("workspaceId", UUID.fromString(WORKSPACE_ID)) }
        verify { spec.bind("id", UUID.fromString(SCHEDULE_ID)) }
    }

    private fun mockWriteSpec(rowsUpdated: Long = 1L): DatabaseClient.GenericExecuteSpec {
        val spec = mockk<DatabaseClient.GenericExecuteSpec>(relaxed = true)
        every { spec.bind(any<String>(), any<Any>()) } returns spec
        every { spec.bindNull(any<String>(), any<Class<*>>()) } returns spec
        val fetchSpec = mockk<FetchSpec<Map<String, Any>>>(relaxed = false)
        every { spec.fetch() } returns fetchSpec
        every { fetchSpec.rowsUpdated() } returns Mono.just(rowsUpdated)
        return spec
    }

    private fun mockReadableRow(id: String, workspaceId: String): Row {
        val row = mockk<Row>(relaxed = false)
        every { row.get("id", UUID::class.java) } returns UUID.fromString(id)
        every { row.get("workspace_id", UUID::class.java) } returns UUID.fromString(workspaceId)
        every { row.get("created_by", UUID::class.java) } returns UUID.fromString(PRINCIPAL_ID)
        every { row.get("template_post_id", UUID::class.java) } returns UUID.fromString(TEMPLATE_ID)
        every { row.get("frequency", String::class.java) } returns "WEEKLY"
        every { row.get("recurrence_interval", Int::class.javaObjectType) } returns 1
        every { row.get("days_of_week", String::class.java) } returns "1,3,5"
        every { row.get("day_of_month", Int::class.javaObjectType) } returns null
        every { row.get("end_date", LocalDate::class.java) } returns LocalDate.parse("2026-12-31")
        every { row.get("max_occurrences", Int::class.javaObjectType) } returns 10
        every { row.get("timezone", String::class.java) } returns "UTC"
        every { row.get("next_scheduled_at", OffsetDateTime::class.java) } returns
            OffsetDateTime.ofInstant(fixedInstant, ZoneId.of("UTC"))
        every { row.get("status", String::class.java) } returns "ACTIVE"
        every { row.get("created_at", OffsetDateTime::class.java) } returns
            OffsetDateTime.ofInstant(fixedInstant, ZoneId.of("UTC"))
        every { row.get("updated_at", OffsetDateTime::class.java) } returns
            OffsetDateTime.ofInstant(fixedInstant, ZoneId.of("UTC"))
        return row
    }

    private companion object {
        const val SCHEDULE_ID = "00000000-0000-0000-0000-000000000001"
        const val SECOND_SCHEDULE_ID = "00000000-0000-0000-0000-000000000002"
        const val WORKSPACE_ID = "00000000-0000-0000-0000-000000000003"
        const val PRINCIPAL_ID = "00000000-0000-0000-0000-000000000004"
        const val TEMPLATE_ID = "00000000-0000-0000-0000-000000000005"
    }
}
