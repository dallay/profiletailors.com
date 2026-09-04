package com.profiletailors.smp.administrative.application

import com.profiletailors.smp.administrative.domain.AdministrativeAuditEvent
import com.profiletailors.smp.administrative.domain.AdministrativeAuditEventRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class AuditEventPublisherTest {

    @Test
    fun `publish delegates to repository save with the given event`() = runTest {
        val repository = mockk<AdministrativeAuditEventRepository>()
        val publisher = AuditEventPublisher(repository)
        val event = AdministrativeAuditEvent(
            id = UUID.randomUUID(),
            actorId = UUID.randomUUID(),
            actorType = "USER",
            action = "workspace.update",
            targetId = "workspace-1",
            targetType = "WORKSPACE",
            correlationId = null,
            metadata = emptyMap(),
            occurredAt = Instant.now(),
        )
        coEvery { repository.save(event) } returns event

        publisher.publish(event)

        coVerify { repository.save(event) }
    }

    @Test
    fun `publish calls save exactly once`() = runTest {
        val repository = mockk<AdministrativeAuditEventRepository>()
        val publisher = AuditEventPublisher(repository)
        val event = AdministrativeAuditEvent(
            id = UUID.randomUUID(),
            actorId = UUID.randomUUID(),
            actorType = "USER",
            action = "role.assign",
            targetId = "role-1",
            targetType = "PLATFORM_ROLE",
            correlationId = "corr-1",
            metadata = mapOf("roleName" to "admin"),
            occurredAt = Instant.now(),
        )
        coEvery { repository.save(event) } returns event

        publisher.publish(event)

        coVerify { repository.save(event) }
    }
}
