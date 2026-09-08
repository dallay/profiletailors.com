package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.publishing.domain.PublicationJobRepository
import com.profiletailors.smp.publishing.domain.PublicationRepository
import com.profiletailors.smp.publishing.domain.PublicationSchedulingPolicy
import com.profiletailors.smp.publishing.domain.RecurrenceRule
import com.profiletailors.smp.publishing.domain.RecurringSchedule
import com.profiletailors.smp.publishing.domain.RecurringScheduleRepository
import com.profiletailors.smp.publishing.domain.RecurringScheduleStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class RecurringScheduleHandlersTest {

    private val workspaceId = "workspace-1"
    private val principalId = UUID.randomUUID().toString()
    private val clock = Clock.fixed(Instant.parse("2026-08-27T12:00:00Z"), ZoneId.of("UTC"))

    @Test
    fun `update handler throws not found when schedule missing`() {
        val repo = mockk<RecurringScheduleRepository>()
        coEvery { repo.findByWorkspaceAndId(workspaceId, "missing-id") } returns null

        val handler = UpdateRecurringScheduleHandler(
            principalContextProvider = FakePrincipalProvider(principalId),
            resourceContextProvider = FakeResourceProvider(workspaceId),
            scheduleRepository = repo,
            clock = clock,
        )

        assertThrows(RecurringScheduleNotFoundException::class.java) {
            runBlocking { handler.handle(UpdateRecurringScheduleCommand(id = "missing-id")) }
        }
    }

    @Test
    fun `delete handler throws not found when schedule missing`() {
        val repo = mockk<RecurringScheduleRepository>()
        coEvery { repo.delete(workspaceId, "missing-id") } returns false

        val handler = DeleteRecurringScheduleHandler(
            principalContextProvider = FakePrincipalProvider(principalId),
            resourceContextProvider = FakeResourceProvider(workspaceId),
            scheduleRepository = repo,
        )

        assertThrows(RecurringScheduleNotFoundException::class.java) {
            runBlocking { handler.handle(DeleteRecurringScheduleCommand(id = "missing-id")) }
        }
    }

    @Test
    fun `create handler creates recurring schedule with active status`() {
        val repo = mockk<RecurringScheduleRepository>()
        val pubRepo = mockk<PublicationRepository>()
        val jobRepo = mockk<PublicationJobRepository>()
        val txRunner = mockk<AtomicTransactionRunner>()
        val schedulingPolicy = mockk<PublicationSchedulingPolicy>()
        coEvery { repo.findByWorkspaceAndId(workspaceId, "pub-1") } returns mockk(relaxed = true) {
            coEvery { status } returns RecurringScheduleStatus.ACTIVE
        }
        coEvery { txRunner.runAtomically(any<suspend () -> Unit>()) } answers {
            runBlocking { (firstArg() as suspend () -> Unit).invoke() }
        }
        coEvery { repo.create(any()) } returns mockk()
        coEvery { pubRepo.createDraft(any()) } returns mockk()
        coEvery { jobRepo.enqueue(any()) } returns mockk()

        val handler = CreateRecurringScheduleHandler(
            principalContextProvider = FakePrincipalProvider(principalId),
            resourceContextProvider = FakeResourceProvider(workspaceId),
            scheduleRepository = repo,
            publicationRepository = pubRepo,
            publicationJobRepository = jobRepo,
            schedulingPolicy = schedulingPolicy,
            transactionRunner = txRunner,
            clock = clock,
        )

        val rule = RecurrenceRule(
            frequency = com.profiletailors.smp.publishing.domain.RecurrenceFrequency.DAILY,
            interval = 1,
            daysOfWeek = emptySet(),
            dayOfMonth = null,
            endDate = null,
            maxOccurrences = null,
        )
        val command = CreateRecurringScheduleCommand(
            templatePostId = "pub-1",
            recurrenceRule = rule,
            startsAt = Instant.parse("2026-09-10T10:00:00Z"),
        )

        val result = runBlocking { handler.handle(command) }

        assertNotNull(result)
        coVerify { repo.create(match { it.status == RecurringScheduleStatus.ACTIVE }) }
    }

    @Test
    fun `list handler returns workspace schedules`() {
        val repo = mockk<RecurringScheduleRepository>()
        val schedule = RecurringSchedule(
            id = "recur-1",
            workspaceId = workspaceId,
            createdBy = principalId,
            templatePostId = "pub-1",
            recurrenceRule = RecurrenceRule(
                frequency = com.profiletailors.smp.publishing.domain.RecurrenceFrequency.WEEKLY,
                interval = 1,
                daysOfWeek = setOf(1),
                dayOfMonth = null,
                endDate = null,
                maxOccurrences = null,
            ),
            timezone = "UTC",
            nextScheduledAt = Instant.parse("2026-09-10T10:00:00Z"),
            status = RecurringScheduleStatus.ACTIVE,
            createdAt = Instant.parse("2026-08-27T12:00:00Z"),
            updatedAt = Instant.parse("2026-08-27T12:00:00Z"),
        )
        coEvery { repo.findByWorkspace(workspaceId) } returns listOf(schedule)

        val handler = ListRecurringSchedulesHandler(
            resourceContextProvider = FakeResourceProvider(workspaceId),
            scheduleRepository = repo,
        )

        val response = runBlocking { handler.handle(ListRecurringSchedulesQuery) }

        assertEquals(1, response.schedules.size)
        assertEquals("recur-1", response.schedules[0].id)
    }

    private class FakePrincipalProvider(private val id: String) : PrincipalContextProvider {
        override suspend fun current(): PrincipalContext? = PrincipalContext(
            principalId = id,
            principalType = PrincipalType.USER,
            subject = "user@example.com",
            provider = "https://issuer.example",
        )
    }

    private class FakeResourceProvider(private val wid: String) : ResourceContextProvider {
        override fun current(): ResourceContext? = ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = wid,
        )
    }
}
