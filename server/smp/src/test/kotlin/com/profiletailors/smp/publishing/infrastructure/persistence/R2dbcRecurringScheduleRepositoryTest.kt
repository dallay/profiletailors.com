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
import java.util.function.BiFunction

class R2dbcRecurringScheduleRepositoryTest {

    private val databaseClient = mockk<DatabaseClient>()
    private val repository = R2dbcRecurringScheduleRepository(databaseClient)
    private val fixedInstant = Instant.parse("2026-01-01T12:00:00Z")

    private fun sampleSchedule(
        id: String = "sched-1",
        workspaceId: String = "ws-1",
        templatePostId: String = "tmpl-1",
    ) = RecurringSchedule(
        id = id,
        workspaceId = workspaceId,
        createdBy = "user-1",
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
        verify { spec.bind("id", "sched-1") }
        verify { spec.bind("workspaceId", "ws-1") }
        verify { spec.bind("frequency", "WEEKLY") }
        verify { spec.bind("interval", 1) }
        verify { spec.bind("daysOfWeek", "1,3,5") }
        verify { spec.bind("timezone", "UTC") }
        verify { spec.bind("status", "ACTIVE") }
        verify { spec.bind("createdBy", "user-1") }
        verify { spec.bind("templatePostId", "tmpl-1") }
    }

    @Test
    fun `update binds fields and updates existing record`() = runTest {
        val schedule = sampleSchedule()
        val spec = mockWriteSpec(rowsUpdated = 1L)
        every { databaseClient.sql(ofType(String::class)) } returns spec

        val result = repository.update(schedule)

        assertEquals(schedule.id, result.id)
        verify { spec.bind("id", "sched-1") }
        verify { spec.bind("workspaceId", "ws-1") }
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
            val row = mockReadableRow("sched-1", "ws-1")
            Flux.just(fn.apply(row, mockk()))
        }

        val result = repository.findByWorkspaceAndId("ws-1", "sched-1")

        assertNotNull(result)
        assertEquals("sched-1", result?.id)
        assertEquals("ws-1", result?.workspaceId)
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

        val result = repository.findByWorkspaceAndId("ws-1", "sched-999")

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
            val row1 = mockReadableRow("sched-1", "ws-1")
            val row2 = mockReadableRow("sched-2", "ws-1")
            Flux.just(fn.apply(row1, mockk()), fn.apply(row2, mockk()))
        }

        val result = repository.findByWorkspace("ws-1")

        assertEquals(2, result.size)
        assertEquals("sched-1", result[0].id)
        assertEquals("sched-2", result[1].id)
    }

    @Test
    fun `pauseByTemplatePost executes update query`() = runTest {
        val spec = mockWriteSpec(rowsUpdated = 1L)
        every { databaseClient.sql(ofType(String::class)) } returns spec

        repository.pauseByTemplatePost("ws-1", "tmpl-1")

        verify { spec.bind("paused", "PAUSED") }
        verify { spec.bind("workspaceId", "ws-1") }
        verify { spec.bind("templatePostId", "tmpl-1") }
        verify { spec.bind("active", "ACTIVE") }
    }

    @Test
    fun `delete returns true when rows updated`() = runTest {
        val spec = mockWriteSpec(rowsUpdated = 1L)
        every { databaseClient.sql(ofType(String::class)) } returns spec

        val deleted = repository.delete("ws-1", "sched-1")

        assertTrue(deleted)
        verify { spec.bind("cancelled", "CANCELLED") }
        verify { spec.bind("existingCancelled", "CANCELLED") }
        verify { spec.bind("workspaceId", "ws-1") }
        verify { spec.bind("id", "sched-1") }
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
        every { row.get("id", String::class.java) } returns id
        every { row.get("workspace_id", String::class.java) } returns workspaceId
        every { row.get("created_by", String::class.java) } returns "user-1"
        every { row.get("template_post_id", String::class.java) } returns "tmpl-1"
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
}
