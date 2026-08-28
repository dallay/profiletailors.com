package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.publishing.domain.RecurringScheduleRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
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
