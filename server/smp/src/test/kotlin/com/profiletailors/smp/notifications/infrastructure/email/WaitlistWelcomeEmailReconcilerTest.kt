package com.profiletailors.smp.notifications.infrastructure.email

import com.profiletailors.leadcapture.waitlist.application.WaitlistWithdrawalUrlProvider
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import com.profiletailors.notifications.application.ports.EmailDispatchResult
import com.profiletailors.notifications.application.ports.EmailDispatcher
import com.profiletailors.notifications.domain.IdempotencyKey
import com.profiletailors.notifications.domain.Notification
import com.profiletailors.notifications.domain.NotificationChannel
import com.profiletailors.notifications.domain.NotificationId
import com.profiletailors.notifications.domain.NotificationPayload
import com.profiletailors.notifications.domain.NotificationRepository
import com.profiletailors.notifications.domain.NotificationStatus
import com.profiletailors.notifications.domain.Recipient
import com.profiletailors.notifications.domain.WelcomeEmailTemplateId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class WaitlistWelcomeEmailReconcilerTest {

    private val fixedNow = Instant.parse("2026-07-20T10:00:00Z")
    private val clock = Clock.fixed(fixedNow, ZoneOffset.UTC)

    @Test
    fun `resolves withdrawal URL and sends a claimed pending welcome notification`() = runTest {
        val repository = mockk<NotificationRepository>()
        val dispatcher = mockk<EmailDispatcher>()
        val withdrawalUrls = mockk<WaitlistWithdrawalUrlProvider>()
        val claimed = notification()
        coEvery {
            repository.claimPending(
                WelcomeEmailTemplateId.INSTANCE,
                fixedNow,
                fixedNow.minus(Duration.ofMinutes(5)),
                20,
            )
        } returns listOf(claimed)
        coEvery { withdrawalUrls.urlFor(WaitlistEntryId("entry-1"), fixedNow) } returns
            "https://profiletailors.com/waitlist/withdraw?token=opaque"
        coEvery { dispatcher.dispatch(any(), any()) } returns EmailDispatchResult.Success
        coEvery { repository.update(any()) } answers { firstArg() }

        WaitlistWelcomeEmailReconciler(
            dispatcher,
            repository,
            withdrawalUrls,
            clock,
        ).reconcile()

        coVerify(exactly = 1) {
            dispatcher.dispatch(
                "user@example.com",
                match { it.text.contains("https://profiletailors.com/waitlist/withdraw?token=opaque") },
            )
        }
        coVerify(exactly = 1) {
            repository.update(match { it.status == NotificationStatus.SENT })
        }
    }

    @Test
    fun `returns claimed notification to pending when withdrawal URL remains unavailable`() = runTest {
        val repository = mockk<NotificationRepository>()
        val dispatcher = mockk<EmailDispatcher>()
        val withdrawalUrls = mockk<WaitlistWithdrawalUrlProvider>()
        val claimed = notification()
        coEvery { repository.claimPending(any(), any(), any(), any()) } returns listOf(claimed)
        coEvery { withdrawalUrls.urlFor(WaitlistEntryId("entry-1"), fixedNow) } returns null
        coEvery { repository.update(any()) } answers { firstArg() }

        WaitlistWelcomeEmailReconciler(
            dispatcher,
            repository,
            withdrawalUrls,
            clock,
        ).reconcile()

        coVerify(exactly = 0) { dispatcher.dispatch(any(), any()) }
        coVerify(exactly = 1) {
            repository.update(
                match {
                    it.status == NotificationStatus.PENDING &&
                        it.updatedAt == fixedNow &&
                        it.errorMessage == null
                },
            )
        }
    }

    private fun notification(): Notification = Notification(
        id = NotificationId("notification-1"),
        idempotencyKey = IdempotencyKey("waitlist.welcome:entry-1"),
        channel = NotificationChannel.EMAIL,
        recipient = Recipient("user@example.com"),
        templateId = WelcomeEmailTemplateId.INSTANCE,
        payload = NotificationPayload(
            mapOf(
                "waitlistEntryId" to "entry-1",
                "waitlistName" to "Profile Tailors Launch",
                "locale" to "en",
            ),
        ),
        status = NotificationStatus.DISPATCHING,
        sentAt = null,
        failedAt = null,
        errorMessage = null,
        createdAt = fixedNow.minusSeconds(30),
        updatedAt = fixedNow,
    )
}
