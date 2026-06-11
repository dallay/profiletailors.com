package com.profiletailors.smp.publishing.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class ConflictDetectionPolicyTest {

    private val baseScheduled = PublicationDraft(
        id = "pub-1",
        workspaceId = "workspace-1",
        authorPrincipalId = "principal-1",
        provider = SocialProvider.LINKEDIN,
        socialAccountId = "acc-li-1",
        status = PublicationStatus.SCHEDULED,
        scheduleMode = ScheduleMode.SCHEDULED_AT,
        priority = false,
        bodyText = "Post 1",
        scheduledFor = Instant.parse("2026-06-09T10:00:00Z"),
    )

    @Test
    fun `adjacent same-account publications within window are flagged`() {
        val pub1 = baseScheduled.copy(id = "pub-1", scheduledFor = Instant.parse("2026-06-09T10:00:00Z"))
        val pub2 = baseScheduled.copy(id = "pub-2", scheduledFor = Instant.parse("2026-06-09T10:10:00Z"))

        val conflicts = ConflictDetectionPolicy.findConflicts(
            publications = listOf(pub1, pub2),
            conflictWindow = Duration.ofMinutes(15),
        )

        assertEquals(setOf("pub-2"), conflicts["pub-1"]?.toSet())
        assertEquals(setOf("pub-1"), conflicts["pub-2"]?.toSet())
    }

    @Test
    fun `publications at exact boundary are not flagged`() {
        val pub1 = baseScheduled.copy(id = "pub-1", scheduledFor = Instant.parse("2026-06-09T10:00:00Z"))
        val pub2 = baseScheduled.copy(id = "pub-2", scheduledFor = Instant.parse("2026-06-09T10:15:00Z"))

        val conflicts = ConflictDetectionPolicy.findConflicts(
            publications = listOf(pub1, pub2),
            conflictWindow = Duration.ofMinutes(15),
        )

        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun `publications across different accounts do not conflict`() {
        val pub1 = baseScheduled.copy(
            id = "pub-1",
            socialAccountId = "acc-li-1",
            scheduledFor = Instant.parse("2026-06-09T10:00:00Z"),
        )
        val pub2 = baseScheduled.copy(
            id = "pub-2",
            socialAccountId = "acc-li-2",
            scheduledFor = Instant.parse("2026-06-09T10:00:00Z"),
        )

        val conflicts = ConflictDetectionPolicy.findConflicts(listOf(pub1, pub2))

        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun `draft failed cancelled and published statuses are skipped`() {
        val now = Instant.parse("2026-06-09T10:00:00Z")
        val draft = baseScheduled.copy(id = "pub-draft", status = PublicationStatus.DRAFT, scheduledFor = now)
        val failed = baseScheduled.copy(id = "pub-failed", status = PublicationStatus.FAILED, scheduledFor = now)
        val cancelled = baseScheduled.copy(id = "pub-cancelled", status = PublicationStatus.CANCELLED, scheduledFor = now)
        val published = baseScheduled.copy(id = "pub-published", status = PublicationStatus.PUBLISHED, scheduledFor = now)

        val conflicts = ConflictDetectionPolicy.findConflicts(
            listOf(draft, failed, cancelled, published),
        )

        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun `null scheduledFor is skipped`() {
        val noTime = baseScheduled.copy(id = "pub-1", scheduledFor = null)
        val withTime = baseScheduled.copy(id = "pub-2", scheduledFor = Instant.parse("2026-06-09T10:00:00Z"))

        val conflicts = ConflictDetectionPolicy.findConflicts(listOf(noTime, withTime))

        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun `empty list returns empty map`() {
        val conflicts = ConflictDetectionPolicy.findConflicts(emptyList())
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun `non-overlapping skipped by wider separation`() {
        val pub1 = baseScheduled.copy(id = "pub-1", scheduledFor = Instant.parse("2026-06-09T10:00:00Z"))
        val pub2 = baseScheduled.copy(id = "pub-2", scheduledFor = Instant.parse("2026-06-09T12:00:00Z"))

        val conflicts = ConflictDetectionPolicy.findConflicts(listOf(pub1, pub2))

        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun `three adjacent within window all conflict`() {
        val pub1 = baseScheduled.copy(id = "pub-1", scheduledFor = Instant.parse("2026-06-09T10:00:00Z"))
        val pub2 = baseScheduled.copy(id = "pub-2", scheduledFor = Instant.parse("2026-06-09T10:05:00Z"))
        val pub3 = baseScheduled.copy(id = "pub-3", scheduledFor = Instant.parse("2026-06-09T10:10:00Z"))

        val conflicts = ConflictDetectionPolicy.findConflicts(
            listOf(pub1, pub2, pub3),
            conflictWindow = Duration.ofMinutes(15),
        )

        assertEquals(setOf("pub-2"), conflicts["pub-1"]?.toSet())
        assertTrue(conflicts["pub-2"]?.containsAll(listOf("pub-1", "pub-3")) ?: false)
        assertEquals(setOf("pub-2"), conflicts["pub-3"]?.toSet())
    }

    @Test
    fun `queued publications also participate in conflict detection`() {
        val queued1 = baseScheduled.copy(id = "pub-q1", status = PublicationStatus.QUEUED,
            scheduledFor = Instant.parse("2026-06-09T10:00:00Z"))
        val queued2 = baseScheduled.copy(id = "pub-q2", status = PublicationStatus.QUEUED,
            scheduledFor = Instant.parse("2026-06-09T10:10:00Z"))

        val conflicts = ConflictDetectionPolicy.findConflicts(
            listOf(queued1, queued2),
            conflictWindow = Duration.ofMinutes(15),
        )

        assertEquals(setOf("pub-q2"), conflicts["pub-q1"]?.toSet())
    }
}
