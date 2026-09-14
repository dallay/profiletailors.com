package com.profiletailors.smp.platformadmin.infrastructure.observability

import com.profiletailors.smp.platformadmin.domain.InvitationTarget
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InvitationObservabilityTest {

    @Test
    fun `records accepted invitations by bounded target`() {
        val meterRegistry = SimpleMeterRegistry()
        val observability = InvitationObservability(meterRegistry)

        observability.recordInvitationAccepted(InvitationTarget.NEW_WORKSPACE)
        observability.recordInvitationAccepted(InvitationTarget.NEW_WORKSPACE)
        observability.recordInvitationAccepted(InvitationTarget.EXISTING_WORKSPACE)

        val newWorkspaceCounter = meterRegistry.find(ACCEPTED_METRIC_NAME)
            .tag("target", InvitationTarget.NEW_WORKSPACE.name)
            .counter()
        val existingWorkspaceCounter = meterRegistry.find(ACCEPTED_METRIC_NAME)
            .tag("target", InvitationTarget.EXISTING_WORKSPACE.name)
            .counter()

        assertEquals(2.0, requireNotNull(newWorkspaceCounter).count())
        assertEquals(1.0, requireNotNull(existingWorkspaceCounter).count())
    }

    @Test
    fun `records expired and replay rejected outcomes`() {
        val meterRegistry = SimpleMeterRegistry()
        val observability = InvitationObservability(meterRegistry)

        observability.recordInvitationExpired()
        observability.recordInvitationReplayRejected()

        assertEquals(
            1.0,
            requireNotNull(meterRegistry.find(EXPIRED_METRIC_NAME).counter()).count(),
        )
        assertEquals(
            1.0,
            requireNotNull(meterRegistry.find(REPLAY_REJECTED_METRIC_NAME).counter()).count(),
        )
    }

    @Test
    fun `retains created and revoked counters without sensitive dimensions`() {
        val meterRegistry = SimpleMeterRegistry()
        val observability = InvitationObservability(meterRegistry)

        observability.recordInvitationCreated()
        observability.recordInvitationRevoked()

        assertEquals(1.0, requireNotNull(meterRegistry.find(CREATED_METRIC_NAME).counter()).count())
        assertEquals(1.0, requireNotNull(meterRegistry.find(REVOKED_METRIC_NAME).counter()).count())
        val tagValues = meterRegistry.meters
            .flatMap { meter -> meter.id.tags }
            .map { tag -> tag.value }
        assertTrue(tagValues.none { value -> value.contains("@") || value.contains("token", ignoreCase = true) })
    }

    private companion object {
        const val ACCEPTED_METRIC_NAME = "platform.invitations.accepted"
        const val CREATED_METRIC_NAME = "platform.invitations.created"
        const val EXPIRED_METRIC_NAME = "platform.invitations.expired"
        const val REPLAY_REJECTED_METRIC_NAME = "platform.invitations.replay_rejected"
        const val REVOKED_METRIC_NAME = "platform.invitations.revoked"
    }
}
