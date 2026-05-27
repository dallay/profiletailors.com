package com.profiletailors.smp.publishing.domain

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class PublicationLifecyclePolicyTest {

    private val baseDraft = PublicationDraft(
        id = "pub-1",
        workspaceId = "workspace-1",
        authorPrincipalId = "principal-1",
        provider = SocialProvider.LINKEDIN,
        socialAccountId = "account-1",
        status = PublicationStatus.DRAFT,
        scheduleMode = ScheduleMode.NOW,
        priority = false,
        bodyText = "Hello LinkedIn",
    )

    @Test
    fun `queues now publication immediately`() {
        val queued = PublicationLifecyclePolicy.queue(baseDraft, Instant.parse("2026-05-26T12:00:00Z"))

        assertEquals(PublicationStatus.QUEUED, queued.status)
        assertEquals(Instant.parse("2026-05-26T12:00:00Z"), queued.scheduledFor)
    }

    @Test
    fun `queues scheduled publication as scheduled`() {
        val draft = baseDraft.copy(
            scheduleMode = ScheduleMode.SCHEDULED_AT,
            scheduledFor = Instant.parse("2026-05-27T08:00:00Z"),
        )

        val queued = PublicationLifecyclePolicy.queue(draft, Instant.parse("2026-05-26T12:00:00Z"))

        assertEquals(PublicationStatus.SCHEDULED, queued.status)
        assertEquals(Instant.parse("2026-05-27T08:00:00Z"), queued.scheduledFor)
    }

    @Test
    fun `prevents editing once processing has started`() {
        val processing = baseDraft.copy(status = PublicationStatus.PROCESSING)

        assertThrows(PublicationEditNotAllowedException::class.java) {
            PublicationLifecyclePolicy.requireEditable(processing)
        }
    }

    @Test
    fun `prevents cancelling once processing has started`() {
        val processing = baseDraft.copy(status = PublicationStatus.PROCESSING)

        assertThrows(PublicationCancellationNotAllowedException::class.java) {
            PublicationLifecyclePolicy.requireCancellable(processing)
        }
    }

    @Test
    fun `allows retry only from failed state`() {
        val failed = baseDraft.copy(status = PublicationStatus.FAILED)
        PublicationLifecyclePolicy.requireRetryable(failed)

        assertThrows(PublicationRetryNotAllowedException::class.java) {
            PublicationLifecyclePolicy.requireRetryable(baseDraft)
        }
    }

    @Test
    fun `priority ordering yields higher rank for priority jobs`() {
        val schedulingPolicy = PublicationSchedulingPolicy()

        assertTrue(schedulingPolicy.priorityRank(baseDraft.copy(priority = true)) > schedulingPolicy.priorityRank(baseDraft))
    }

    @Test
    fun `retry policy retries only within budget`() = runTest {
        val retryPolicy = DeliveryRetryPolicy(maxRetries = 2, retryBackoff = Duration.ofMinutes(5))

        assertTrue(retryPolicy.shouldRetry(currentAttemptNumber = 1, retryable = true))
        assertTrue(retryPolicy.shouldRetry(currentAttemptNumber = 2, retryable = true))
        assertEquals(false, retryPolicy.shouldRetry(currentAttemptNumber = 3, retryable = true))
        assertEquals(Instant.parse("2026-05-26T12:05:00Z"), retryPolicy.nextRetryAt(Instant.parse("2026-05-26T12:00:00Z")))
        assertEquals(3, retryPolicy.maxAttempts())
    }
}
