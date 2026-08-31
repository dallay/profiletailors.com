package com.profiletailors.smp.notifications.infrastructure.email

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventConsumer
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.common.domain.bus.event.Subscribe
import com.profiletailors.notifications.application.ports.EmailDispatchResult
import com.profiletailors.notifications.application.ports.EmailDispatcher
import com.profiletailors.notifications.domain.IdempotencyKey
import com.profiletailors.notifications.domain.Notification
import com.profiletailors.notifications.domain.NotificationRepository
import com.profiletailors.notifications.domain.NotificationStatus
import com.profiletailors.notifications.domain.event.InvitationCreated
import com.profiletailors.notifications.domain.event.InvitationDeliveryAttempted
import com.profiletailors.notifications.domain.event.InvitationResent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

internal class SendInvitationEmailConsumerTest {

    private val fixedNow = Instant.parse("2026-08-24T10:15:30Z")
    private val clock = Clock.fixed(fixedNow, ZoneOffset.UTC)
    private val inviteeEmail = "invitee@example.com"

    @Test
    fun `both invitation event types are registered as event consumers`() {
        val createdConsumer = SendInvitationEmailConsumer::class.java
        val resentConsumer = SendInvitationResentEmailConsumer::class.java

        assertThat(EventConsumer::class.java.isAssignableFrom(createdConsumer)).isTrue()
        assertThat(createdConsumer.getAnnotation(Subscribe::class.java).filterBy)
            .isEqualTo(InvitationCreated::class)
        assertThat(EventConsumer::class.java.isAssignableFrom(resentConsumer)).isTrue()
        assertThat(resentConsumer.getAnnotation(Subscribe::class.java).filterBy)
            .isEqualTo(InvitationResent::class)
    }

    @Test
    fun `success dispatch persists as SENT and publishes delivery event with SENT`() = runTest {
        val invitationId = UUID.randomUUID()

        val notificationRepo = mockk<NotificationRepository>(relaxed = true)
        val emailDispatcher = mockk<EmailDispatcher>()
        val eventPublisher = mockk<EventPublisher<DomainEvent>>()

        coEvery { notificationRepo.findByIdempotencyKey(any()) } returns null
        val persistedSlot = slot<Notification>()
        coEvery { notificationRepo.save(capture(persistedSlot)) } answers { persistedSlot.captured }
        val updatedSlot = slot<Notification>()
        coEvery { notificationRepo.update(capture(updatedSlot)) } answers { updatedSlot.captured }
        coEvery { emailDispatcher.dispatch(eq(inviteeEmail), any()) } returns EmailDispatchResult.Success
        val publishedSlot = slot<DomainEvent>()
        coEvery { eventPublisher.publish(capture(publishedSlot)) } returns Unit

        val consumer = SendInvitationEmailConsumer(
            emailDispatcher = emailDispatcher,
            notificationRepository = notificationRepo,
            deliveryEventPublisher = eventPublisher,
            clock = clock,
        )

        consumer.consume(
            InvitationCreated(
                invitationId = invitationId,
                waitlistEntryId = UUID.randomUUID().toString(),
                operatorPrincipalId = UUID.randomUUID(),
                recipient = inviteeEmail,
                workspaceName = "Profile Tailors Beta",
                acceptUrl = "https://app.example.com/invitations/accept?token=raw-token-abc",
                locale = "es",
                rawToken = "raw-token-abc",
            ),
        )

        assertThat(updatedSlot.captured.status).isEqualTo(NotificationStatus.SENT)
        assertThat(persistedSlot.captured.idempotencyKey).isEqualTo(
            IdempotencyKey("platform.invitation:$invitationId"),
        )
        assertThat(publishedSlot.captured).isInstanceOf(InvitationDeliveryAttempted::class.java)
        val published = publishedSlot.captured as InvitationDeliveryAttempted
        assertThat(published.invitationId).isEqualTo(invitationId)
        assertThat(published.status).isEqualTo("SENT")
    }

    @Test
    fun `delivery failure persists FAILED and publishes InvitationDeliveryAttempted with FAILED`() = runTest {
        val invitationId = UUID.randomUUID()

        val notificationRepo = mockk<NotificationRepository>(relaxed = true)
        val emailDispatcher = mockk<EmailDispatcher>()
        val eventPublisher = mockk<EventPublisher<DomainEvent>>()

        coEvery { notificationRepo.findByIdempotencyKey(any()) } returns null
        val persistedSlot = slot<Notification>()
        coEvery { notificationRepo.save(capture(persistedSlot)) } answers { persistedSlot.captured }
        val updatedSlot = slot<Notification>()
        coEvery { notificationRepo.update(capture(updatedSlot)) } answers { updatedSlot.captured }
        coEvery { emailDispatcher.dispatch(eq(inviteeEmail), any()) } returns
            EmailDispatchResult.Failure(error = "Resend API key rejected")
        val publishedSlot = slot<DomainEvent>()
        coEvery { eventPublisher.publish(capture(publishedSlot)) } returns Unit

        val consumer = SendInvitationEmailConsumer(
            emailDispatcher = emailDispatcher,
            notificationRepository = notificationRepo,
            deliveryEventPublisher = eventPublisher,
            clock = clock,
        )

        consumer.consume(
            InvitationCreated(
                invitationId = invitationId,
                waitlistEntryId = UUID.randomUUID().toString(),
                operatorPrincipalId = UUID.randomUUID(),
                recipient = inviteeEmail,
                workspaceName = "Profile Tailors Beta",
                acceptUrl = "https://app.example.com/invitations/accept?token=raw-token-abc",
                locale = "en",
                rawToken = "raw-token-abc",
            ),
        )

        assertThat(updatedSlot.captured.status).isEqualTo(NotificationStatus.FAILED)
        assertThat(updatedSlot.captured.errorMessage).contains("Resend API key rejected")
        assertThat(publishedSlot.captured).isInstanceOf(InvitationDeliveryAttempted::class.java)
        val published = publishedSlot.captured as InvitationDeliveryAttempted
        assertThat(published.status).isEqualTo("FAILED")
    }

    @Test
    fun `duplicate dispatch is a no-op for email but still publishes a SENT outcome for idempotent state`() = runTest {
        val invitationId = UUID.randomUUID()
        val existingNotification = mockk<Notification>(relaxed = true)
        val notificationRepo = mockk<NotificationRepository>()
        val emailDispatcher = mockk<EmailDispatcher>()
        val eventPublisher = mockk<EventPublisher<DomainEvent>>()

        coEvery {
            notificationRepo.findByIdempotencyKey(IdempotencyKey("platform.invitation:$invitationId"))
        } returns existingNotification
        val publishedSlot = slot<DomainEvent>()
        coEvery { eventPublisher.publish(capture(publishedSlot)) } returns Unit

        val consumer = SendInvitationEmailConsumer(
            emailDispatcher = emailDispatcher,
            notificationRepository = notificationRepo,
            deliveryEventPublisher = eventPublisher,
            clock = clock,
        )

        consumer.consume(
            InvitationCreated(
                invitationId = invitationId,
                waitlistEntryId = UUID.randomUUID().toString(),
                operatorPrincipalId = UUID.randomUUID(),
                recipient = inviteeEmail,
                workspaceName = "Profile Tailors Beta",
                acceptUrl = "https://app.example.com/invitations/accept?token=raw-token-abc",
                locale = null,
                rawToken = "raw-token-abc",
            ),
        )

        coVerify(exactly = 0) { emailDispatcher.dispatch(any(), any()) }
        coVerify(exactly = 0) { notificationRepo.save(any()) }
        assertThat(publishedSlot.captured).isInstanceOf(InvitationDeliveryAttempted::class.java)
        val published = publishedSlot.captured as InvitationDeliveryAttempted
        assertThat(published.invitationId).isEqualTo(invitationId)
        assertThat(published.status).isEqualTo("SENT")
    }

    @Test
    fun `InvitationResent follows the same flow through its event consumer`() = runTest {
        val newInvitationId = UUID.randomUUID()

        val notificationRepo = mockk<NotificationRepository>(relaxed = true)
        val emailDispatcher = mockk<EmailDispatcher>()
        val eventPublisher = mockk<EventPublisher<DomainEvent>>()

        coEvery { notificationRepo.findByIdempotencyKey(any()) } returns null
        val persistedSlot = slot<Notification>()
        coEvery { notificationRepo.save(capture(persistedSlot)) } answers { persistedSlot.captured }
        coEvery { notificationRepo.update(any()) } answers { firstArg() }
        coEvery { emailDispatcher.dispatch(eq(inviteeEmail), any()) } returns EmailDispatchResult.Success
        val publishedSlot = slot<DomainEvent>()
        coEvery { eventPublisher.publish(capture(publishedSlot)) } returns Unit

        val consumer = SendInvitationEmailConsumer(
            emailDispatcher = emailDispatcher,
            notificationRepository = notificationRepo,
            deliveryEventPublisher = eventPublisher,
            clock = clock,
        )
        val resentConsumer = SendInvitationResentEmailConsumer(consumer)

        resentConsumer.consume(
            InvitationResent(
                invitationId = newInvitationId,
                waitlistEntryId = UUID.randomUUID().toString(),
                operatorPrincipalId = UUID.randomUUID(),
                recipient = inviteeEmail,
                workspaceName = "Profile Tailors Beta",
                acceptUrl = "https://app.example.com/invitations/accept?token=raw-token-xyz",
                locale = "es",
                rawToken = "raw-token-xyz",
                previousInvitationId = UUID.randomUUID(),
            ),
        )

        assertThat(persistedSlot.captured.idempotencyKey).isEqualTo(
            IdempotencyKey("platform.invitation:$newInvitationId"),
        )
        assertThat(publishedSlot.captured).isInstanceOf(InvitationDeliveryAttempted::class.java)
        val published = publishedSlot.captured as InvitationDeliveryAttempted
        assertThat(published.invitationId).isEqualTo(newInvitationId)
    }
}
