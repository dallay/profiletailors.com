package com.profiletailors.smp.platformadmin.infrastructure.events

import com.profiletailors.smp.platformadmin.domain.InvitationIssued
import com.profiletailors.smp.platformadmin.domain.InvitationTarget
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEvent
import org.springframework.context.PayloadApplicationEvent
import org.springframework.transaction.reactive.TransactionContext
import org.springframework.transaction.reactive.TransactionalEventPublisher
import reactor.core.publisher.Mono
import java.util.UUID
import java.util.function.Function

internal class SpringTransactionalInvitationEventPublisherTest {

    private val transactionalPublisher = mockk<TransactionalEventPublisher>()
    private val publisher = SpringTransactionalInvitationEventPublisher(transactionalPublisher)

    private fun issuedEvent() = InvitationIssued(
        invitationId = UUID.randomUUID(),
        recipientEmail = "invitee@example.com",
        workspaceName = "Workspace",
        target = InvitationTarget.EXISTING_WORKSPACE,
        locale = null,
        rawToken = "raw-token",
    )

    @Test
    fun `publishes domain event with reactive transaction context`() = runTest {
        val functionSlot = slot<Function<TransactionContext, ApplicationEvent>>()
        every { transactionalPublisher.publishEvent(capture(functionSlot)) } returns Mono.empty()

        val event = issuedEvent()
        publisher.publish(event)

        verify(exactly = 1) { transactionalPublisher.publishEvent(any()) }
        val context = mockk<TransactionContext>()
        val published = functionSlot.captured.apply(context)
        val payload = (published as PayloadApplicationEvent<*>).payload
        assertEquals(event, payload)
    }

    @Test
    fun `propagates publication failure for rollback`() = runTest {
        every { transactionalPublisher.publishEvent(any()) } returns Mono.error(RuntimeException("no transaction"))

        assertThrows<RuntimeException> {
            publisher.publish(issuedEvent())
        }
    }
}
