package com.profiletailors.smp.notifications.infrastructure.email

import com.profiletailors.notifications.application.ports.EmailDispatchResult
import com.profiletailors.notifications.application.ports.EmailDispatcher
import com.profiletailors.notifications.domain.IdempotencyKey
import com.profiletailors.notifications.domain.Notification
import com.profiletailors.notifications.domain.NotificationRepository
import com.profiletailors.notifications.domain.NotificationStatus
import com.profiletailors.notifications.domain.event.InvitationResent
import com.profiletailors.smp.platformadmin.application.contracts.AcceptUrlTemplate
import com.profiletailors.smp.platformadmin.domain.InvitationIssued
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

internal class SendInvitationEmailConsumerTest {

    private val fixedNow = Instant.parse("2026-08-24T10:15:30Z")
    private val clock = Clock.fixed(fixedNow, ZoneOffset.UTC)
    private val inviteeEmail = "invitee@example.com"
    private val workspaceName = "Test Workspace"
    private val acceptUrl = "https://app.profiletailors.com/register?invitation=SECRET-TOKEN"
    private val invitationId = UUID.randomUUID()
    private val previousInvitationId = UUID.randomUUID()
    private val operatorPrincipalId = UUID.randomUUID()
    private val rawToken = "SECRET-TOKEN"

    private val acceptUrlTemplate = mockk<AcceptUrlTemplate> {
        every { build(rawToken) } returns acceptUrl
    }

    private val emailDispatcher = mockk<EmailDispatcher>()
    private val notificationRepository = mockk<NotificationRepository>()
    private val consumer = SendInvitationEmailConsumer(
        emailDispatcher = emailDispatcher,
        notificationRepository = notificationRepository,
        acceptUrlTemplate = acceptUrlTemplate,
        clock = clock,
    )

    @Test
    fun `invitation listeners run after transaction commit`() {
        val issuedMethod = SendInvitationEmailConsumer::class.java.methods.single {
            it.name == "onInvitationIssued"
        }
        val resentMethod = SendInvitationEmailConsumer::class.java.methods.single {
            it.name == "onInvitationResent"
        }

        issuedMethod.getAnnotation(TransactionalEventListener::class.java).phase shouldBe TransactionPhase.AFTER_COMMIT
        resentMethod.getAnnotation(TransactionalEventListener::class.java).phase shouldBe TransactionPhase.AFTER_COMMIT
    }

    @Test
    fun `dispatches invitation email and marks notification sent on success`() = runTest {
        val saved = slot<Notification>()
        val updated = slot<Notification>()
        coEvery { notificationRepository.findByIdempotencyKey(any()) } returns null
        coEvery { notificationRepository.save(capture(saved)) } answers { saved.captured }
        coEvery { notificationRepository.update(capture(updated)) } answers { updated.captured }
        coEvery { emailDispatcher.dispatch(inviteeEmail, any()) } returns EmailDispatchResult.Success

        consumer.onInvitationIssued(
            InvitationIssued(
                invitationId = invitationId,
                recipientEmail = inviteeEmail,
                workspaceName = workspaceName,
                locale = "en",
                rawToken = rawToken,
            ),
        )

        saved.captured.status shouldBe NotificationStatus.PENDING
        updated.captured.status shouldBe NotificationStatus.SENT
        saved.captured.payload.variables.containsKey("rawToken") shouldBe false
        saved.captured.payload.variables.containsValue(rawToken) shouldBe false
        coVerify(exactly = 1) { emailDispatcher.dispatch(inviteeEmail, any()) }
    }

    @Test
    fun `marks notification failed when dispatcher fails`() = runTest {
        val saved = slot<Notification>()
        val updated = slot<Notification>()
        coEvery { notificationRepository.findByIdempotencyKey(any()) } returns null
        coEvery { notificationRepository.save(capture(saved)) } answers { saved.captured }
        coEvery { notificationRepository.update(capture(updated)) } answers { updated.captured }
        coEvery { emailDispatcher.dispatch(inviteeEmail, any()) } returns EmailDispatchResult.Failure("SMTP error")

        consumer.onInvitationIssued(
            InvitationIssued(
                invitationId = invitationId,
                recipientEmail = inviteeEmail,
                workspaceName = workspaceName,
                locale = "en",
                rawToken = rawToken,
            ),
        )

        saved.captured.status shouldBe NotificationStatus.PENDING
        updated.captured.status shouldBe NotificationStatus.FAILED
        updated.captured.errorMessage shouldBe "SMTP error"
    }

    @Test
    fun `uses the initial invitation idempotency key`() = runTest {
        val existing = mockk<Notification>()
        val initialKey = IdempotencyKey("invitation:$invitationId:initial")
        coEvery { notificationRepository.findByIdempotencyKey(initialKey) } returns existing

        consumer.onInvitationIssued(
            InvitationIssued(
                invitationId = invitationId,
                recipientEmail = inviteeEmail,
                workspaceName = workspaceName,
                locale = "en",
                rawToken = rawToken,
            ),
        )

        coVerify(exactly = 1) {
            notificationRepository.findByIdempotencyKey(initialKey)
        }
        coVerify(exactly = 0) { emailDispatcher.dispatch(any(), any()) }
    }

    @Test
    fun `uses the accept URL template to build the delivery URL`() = runTest {
        val saved = slot<Notification>()
        val updated = slot<Notification>()
        coEvery { notificationRepository.findByIdempotencyKey(any()) } returns null
        coEvery { notificationRepository.save(capture(saved)) } answers { saved.captured }
        coEvery { notificationRepository.update(capture(updated)) } answers { updated.captured }
        coEvery { emailDispatcher.dispatch(inviteeEmail, any()) } returns EmailDispatchResult.Success

        consumer.onInvitationIssued(
            InvitationIssued(
                invitationId = invitationId,
                recipientEmail = inviteeEmail,
                workspaceName = workspaceName,
                locale = "en",
                rawToken = rawToken,
            ),
        )

        io.mockk.verify(exactly = 1) { acceptUrlTemplate.build(rawToken) }
        saved.captured.payload.variables["acceptUrl"] shouldBe acceptUrl
    }

    @Test
    fun `skips dispatch when idempotency key already exists`() = runTest {
        val existing = mockk<Notification>()
        coEvery { notificationRepository.findByIdempotencyKey(any()) } returns existing

        consumer.onInvitationIssued(
            InvitationIssued(
                invitationId = invitationId,
                recipientEmail = inviteeEmail,
                workspaceName = workspaceName,
                locale = "en",
                rawToken = rawToken,
            ),
        )

        coVerify(exactly = 0) { emailDispatcher.dispatch(any(), any()) }
        coVerify(exactly = 0) { notificationRepository.save(any()) }
    }

    @Test
    fun `marks a failed notification without changing invitation state`() = runTest {
        val saved = slot<Notification>()
        val updated = slot<Notification>()
        coEvery { notificationRepository.findByIdempotencyKey(any()) } returns null
        coEvery { notificationRepository.save(capture(saved)) } answers { saved.captured }
        coEvery { notificationRepository.update(capture(updated)) } answers { updated.captured }
        coEvery { emailDispatcher.dispatch(inviteeEmail, any()) } returns EmailDispatchResult.Failure("SMTP error")

        consumer.onInvitationIssued(
            InvitationIssued(
                invitationId = invitationId,
                recipientEmail = inviteeEmail,
                workspaceName = workspaceName,
                locale = "en",
                rawToken = rawToken,
            ),
        )

        saved.captured.idempotencyKey.value shouldBe "invitation:$invitationId:initial"
        updated.captured.status shouldBe NotificationStatus.FAILED
        updated.captured.errorMessage shouldBe "SMTP error"
    }

    @Test
    fun `handles invitation resent event`() = runTest {
        val saved = slot<Notification>()
        val updated = slot<Notification>()
        coEvery { notificationRepository.findByIdempotencyKey(any()) } returns null
        coEvery { notificationRepository.save(capture(saved)) } answers { saved.captured }
        coEvery { notificationRepository.update(capture(updated)) } answers { updated.captured }
        coEvery { emailDispatcher.dispatch(inviteeEmail, any()) } returns EmailDispatchResult.Success

        consumer.onInvitationResent(
            InvitationResent(
                invitationId = invitationId,
                waitlistEntryId = "waitlist-123",
                operatorPrincipalId = operatorPrincipalId,
                recipient = inviteeEmail,
                workspaceName = workspaceName,
                acceptUrl = acceptUrl,
                rawToken = rawToken,
                locale = "en",
                previousInvitationId = previousInvitationId,
            ),
        )

        saved.captured.status shouldBe NotificationStatus.PENDING
        updated.captured.status shouldBe NotificationStatus.SENT
        coVerify(exactly = 1) { emailDispatcher.dispatch(inviteeEmail, any()) }
    }
}
