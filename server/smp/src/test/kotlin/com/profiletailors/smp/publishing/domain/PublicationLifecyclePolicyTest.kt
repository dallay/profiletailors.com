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

    // ---------------------------------------------------------------------------
    // Past-time validation tests
    // ---------------------------------------------------------------------------

    @Test
    fun `rejects scheduled publication with past time`() {
        val now = Instant.parse("2026-05-26T12:00:00Z")
        val draft = baseDraft.copy(
            scheduleMode = ScheduleMode.SCHEDULED_AT,
            scheduledFor = Instant.parse("2026-05-26T11:55:00Z"), // 5 minutes in the past
        )

        val exception = assertThrows(IllegalArgumentException::class.java) {
            PublicationLifecyclePolicy.validateForCreation(draft, now)
        }
        assertTrue(exception.message!!.contains("Cannot schedule"))
    }

    @Test
    fun `accepts scheduled publication exactly 5 minutes from now`() {
        val now = Instant.parse("2026-05-26T12:00:00Z")
        val draft = baseDraft.copy(
            scheduleMode = ScheduleMode.SCHEDULED_AT,
            scheduledFor = Instant.parse("2026-05-26T12:05:00Z"), // exactly 5 minutes ahead
        )

        PublicationLifecyclePolicy.validateForCreation(draft, now)
    }

    @Test
    fun `accepts scheduled publication just after now`() {
        val now = Instant.parse("2026-05-26T12:00:00Z")
        val draft = baseDraft.copy(
            scheduleMode = ScheduleMode.SCHEDULED_AT,
            scheduledFor = Instant.parse("2026-05-26T12:00:01Z"), // 1 second ahead
        )

        PublicationLifecyclePolicy.validateForCreation(draft, now)
    }

    @Test
    fun `accepts scheduled publication at current time`() {
        val now = Instant.parse("2026-05-26T12:00:00Z")
        val draft = baseDraft.copy(
            scheduleMode = ScheduleMode.SCHEDULED_AT,
            scheduledFor = now,
        )

        PublicationLifecyclePolicy.validateForCreation(draft, now)
    }

    @Test
    fun `accepts now mode regardless of current time`() {
        val now = Instant.parse("2026-05-26T12:00:00Z")
        // NOW mode with null scheduledFor should pass (no past-time check)
        PublicationLifecyclePolicy.validateForCreation(baseDraft, now)
    }

    @Test
    fun `accepts next slot mode regardless of current time`() {
        val now = Instant.parse("2026-05-26T12:00:00Z")
        val draft = baseDraft.copy(
            scheduleMode = ScheduleMode.NEXT_SLOT,
            nextSlotAfter = Instant.parse("2026-05-26T13:00:00Z"),
        )
        // NEXT_SLOT mode should not trigger past-time check
        PublicationLifecyclePolicy.validateForCreation(draft, now)
    }

    @Test
    fun `rejects scheduled publication with past date but future time`() {
        val now = Instant.parse("2026-05-26T12:00:00Z")
        // Yesterday's date at any time is still in the past as an absolute instant
        val draft = baseDraft.copy(
            scheduleMode = ScheduleMode.SCHEDULED_AT,
            scheduledFor = Instant.parse("2026-05-25T15:00:00Z"), // yesterday at 3pm
        )

        val exception = assertThrows(IllegalArgumentException::class.java) {
            PublicationLifecyclePolicy.validateForCreation(draft, now)
        }
        assertTrue(exception.message!!.contains("Cannot schedule"))
    }

    // ---------------------------------------------------------------------------
    // BLOCKED lifecycle tests
    // ---------------------------------------------------------------------------

    @Test
    fun `markBlocked transitions from QUEUED to BLOCKED`() {
        val now = Instant.parse("2026-05-26T12:00:00Z")
        val queued = baseDraft.copy(status = PublicationStatus.QUEUED)

        val blocked = PublicationLifecyclePolicy.markBlocked(queued, now, "Account is DISABLED")

        assertEquals(PublicationStatus.BLOCKED, blocked.status)
        assertEquals(now, blocked.blockedAt)
        assertEquals("Account is DISABLED", blocked.blockedReason)
    }

    @Test
    fun `markBlocked transitions from PROCESSING to BLOCKED`() {
        val now = Instant.parse("2026-05-26T12:00:00Z")
        val processing = baseDraft.copy(status = PublicationStatus.PROCESSING)

        val blocked = PublicationLifecyclePolicy.markBlocked(processing, now, "Account requires reconnect")

        assertEquals(PublicationStatus.BLOCKED, blocked.status)
        assertEquals(now, blocked.blockedAt)
        assertEquals("Account requires reconnect", blocked.blockedReason)
    }

    @Test
    fun `markBlocked transitions from SCHEDULED to BLOCKED`() {
        val now = Instant.parse("2026-05-26T12:00:00Z")
        val scheduled = baseDraft.copy(status = PublicationStatus.SCHEDULED)

        val blocked = PublicationLifecyclePolicy.markBlocked(scheduled, now, "Account is DISABLED")

        assertEquals(PublicationStatus.BLOCKED, blocked.status)
        assertEquals(now, blocked.blockedAt)
        assertEquals("Account is DISABLED", blocked.blockedReason)
    }

    @Test
    fun `markBlocked rejects terminal PUBLISHED status`() {
        val now = Instant.parse("2026-05-26T12:00:00Z")
        val published = baseDraft.copy(status = PublicationStatus.PUBLISHED)

        assertThrows(PublicationStateTransitionException::class.java) {
            PublicationLifecyclePolicy.markBlocked(published, now, "test")
        }
    }

    @Test
    fun `markBlocked rejects terminal CANCELLED status`() {
        val now = Instant.parse("2026-05-26T12:00:00Z")
        val cancelled = baseDraft.copy(status = PublicationStatus.CANCELLED)

        assertThrows(PublicationStateTransitionException::class.java) {
            PublicationLifecyclePolicy.markBlocked(cancelled, now, "test")
        }
    }

    @Test
    fun `markBlocked rejects re-blocking already BLOCKED publication`() {
        val now = Instant.parse("2026-05-26T12:00:00Z")
        val alreadyBlocked = baseDraft.copy(status = PublicationStatus.BLOCKED, blockedAt = now.minusSeconds(60))

        assertThrows(PublicationStateTransitionException::class.java) {
            PublicationLifecyclePolicy.markBlocked(alreadyBlocked, now, "Updated reason")
        }
    }

    // ---------------------------------------------------------------------------
    // BLOCKED retry / exponential backoff tests
    // ---------------------------------------------------------------------------

    @Test
    fun `prepareBlockedRetry transitions BLOCKED to QUEUED with exponential backoff`() {
        val now = Instant.parse("2026-05-26T12:00:00Z")
        val blocked = baseDraft.copy(
            status = PublicationStatus.BLOCKED,
            blockedAt = now.minusSeconds(60),
            blockedReason = "Account is DISABLED",
            retryCount = 0,
        )

        val retried = PublicationLifecyclePolicy.prepareBlockedRetry(blocked, now)

        assertEquals(PublicationStatus.QUEUED, retried.status)
        assertEquals(1, retried.retryCount)
        assertEquals(null, retried.blockedAt)
        assertEquals(null, retried.blockedReason)
        assertEquals(now.plus(Duration.ofMinutes(1)), retried.scheduledFor) // Initial delay = 1 min
    }

    @Test
    fun `prepareBlockedRetry doubles delay on second retry`() {
        val now = Instant.parse("2026-05-26T12:00:00Z")
        val blocked = baseDraft.copy(
            status = PublicationStatus.BLOCKED,
            retryCount = 1,
        )

        val retried = PublicationLifecyclePolicy.prepareBlockedRetry(blocked, now)

        assertEquals(PublicationStatus.QUEUED, retried.status)
        assertEquals(2, retried.retryCount)
        assertEquals(now.plus(Duration.ofMinutes(2)), retried.scheduledFor) // 2^1 = 2 min
    }

    @Test
    fun `prepareBlockedRetry uses exponential backoff under cap`() {
        val now = Instant.parse("2026-05-26T12:00:00Z")
        val blocked = baseDraft.copy(
            status = PublicationStatus.BLOCKED,
            retryCount = 4,
        )

        val retried = PublicationLifecyclePolicy.prepareBlockedRetry(blocked, now)

        assertEquals(PublicationStatus.QUEUED, retried.status)
        assertEquals(5, retried.retryCount)
        assertEquals(now.plus(Duration.ofMinutes(16)), retried.scheduledFor) // 2^4 = 16 min, under cap
    }

    @Test
    fun `prepareBlockedRetry transitions to FAILED after max retries`() {
        val now = Instant.parse("2026-05-26T12:00:00Z")
        val blocked = baseDraft.copy(
            status = PublicationStatus.BLOCKED,
            retryCount = 5, // Already at max retries
        )

        val failed = PublicationLifecyclePolicy.prepareBlockedRetry(blocked, now)

        assertEquals(PublicationStatus.FAILED, failed.status)
        assertEquals(now, failed.failedAt)
        assertEquals("BLOCKED_MAX_RETRIES_EXCEEDED", failed.lastErrorCode)
        assertTrue(failed.lastErrorMessage!!.contains("maximum retries"))
    }

    @Test
    fun `prepareBlockedRetry rejects non-BLOCKED publication`() {
        val now = Instant.parse("2026-05-26T12:00:00Z")
        val queued = baseDraft.copy(status = PublicationStatus.QUEUED)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            PublicationLifecyclePolicy.prepareBlockedRetry(queued, now)
        }
        assertTrue(exception.message!!.contains("Only BLOCKED publications"))
    }

    @Test
    fun `blockedRetryDelay follows exponential backoff with cap`() {
        // 2^0 = 1 min
        assertEquals(Duration.ofMinutes(1), PublicationLifecyclePolicy.blockedRetryDelay(0))
        // 2^1 = 2 min
        assertEquals(Duration.ofMinutes(2), PublicationLifecyclePolicy.blockedRetryDelay(1))
        // 2^2 = 4 min
        assertEquals(Duration.ofMinutes(4), PublicationLifecyclePolicy.blockedRetryDelay(2))
        // 2^3 = 8 min
        assertEquals(Duration.ofMinutes(8), PublicationLifecyclePolicy.blockedRetryDelay(3))
        // 2^4 = 16 min
        assertEquals(Duration.ofMinutes(16), PublicationLifecyclePolicy.blockedRetryDelay(4))
        // 2^5 = 32 min
        assertEquals(Duration.ofMinutes(32), PublicationLifecyclePolicy.blockedRetryDelay(5))
        // 2^6 = 64, capped to 60 min
        assertEquals(Duration.ofMinutes(60), PublicationLifecyclePolicy.blockedRetryDelay(6))
    }
}
