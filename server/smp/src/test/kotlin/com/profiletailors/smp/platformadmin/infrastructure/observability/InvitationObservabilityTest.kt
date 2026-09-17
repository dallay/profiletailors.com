package com.profiletailors.smp.platformadmin.infrastructure.observability

import com.profiletailors.smp.platformadmin.domain.InvitationTarget
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test

class InvitationObservabilityTest {

    @Test
    fun `records user control authorization and replay outcomes with bounded labels`() {
        val meterRegistry = SimpleMeterRegistry()
        val observability = UserControlObservability(meterRegistry)

        observability.recordAuthorizationRejected("disable")
        observability.recordIdempotencyReplay()
        observability.record("disable", "success")
        observability.record("enable", "failure")
        observability.record("sessions_revoke", "idempotent")

        assertUserControlCounter(meterRegistry, "disable", "rejected")
        assertUserControlCounter(meterRegistry, "idempotency", "idempotent")
        assertUserControlCounter(meterRegistry, "disable", "success")
        assertUserControlCounter(meterRegistry, "enable", "failure")
        assertUserControlCounter(meterRegistry, "sessions_revoke", "idempotent")
        assertAuthorizationFailureCounter(meterRegistry, "disable")
    }

    private fun assertUserControlCounter(meterRegistry: SimpleMeterRegistry, operation: String, outcome: String) {
        val counter = meterRegistry.find("profiletailors.admin.user_control.requests")
            .tag("operation", operation)
            .tag("outcome", outcome)
            .counter()
        requireNotNull(counter).count().shouldBe(1.0)
    }

    private fun assertAuthorizationFailureCounter(meterRegistry: SimpleMeterRegistry, operation: String) {
        val counter = meterRegistry.find("profiletailors.admin.user_control.authorization_failures")
            .tag("operation", operation)
            .counter()
        requireNotNull(counter).count().shouldBe(1.0)
    }

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

        requireNotNull(newWorkspaceCounter).count().shouldBe(2.0)
        requireNotNull(existingWorkspaceCounter).count().shouldBe(1.0)
    }

    @Test
    fun `records expired and replay rejected outcomes`() {
        val meterRegistry = SimpleMeterRegistry()
        val observability = InvitationObservability(meterRegistry)

        observability.recordInvitationExpired()
        observability.recordInvitationReplayRejected()

        requireNotNull(meterRegistry.find(EXPIRED_METRIC_NAME).counter()).count().shouldBe(1.0)
        requireNotNull(meterRegistry.find(REPLAY_REJECTED_METRIC_NAME).counter()).count().shouldBe(1.0)
    }

    @Test
    fun `retains created and revoked counters without sensitive dimensions`() {
        val meterRegistry = SimpleMeterRegistry()
        val observability = InvitationObservability(meterRegistry)

        observability.recordInvitationCreated()
        observability.recordInvitationRevoked()

        meterRegistry.find(CREATED_METRIC_NAME).counter()!!.count().shouldBe(1.0)
        meterRegistry.find(REVOKED_METRIC_NAME).counter()!!.count().shouldBe(1.0)
        val tagValues = meterRegistry.meters
            .flatMap { meter -> meter.id.tags }
            .map { tag -> tag.value }
        tagValues.none { value -> value.contains("@") || value.contains("token", ignoreCase = true) }.shouldBe(true)
    }

    @Test
    fun `records bulk counter with batch size and outcome counts`() {
        val meterRegistry = SimpleMeterRegistry()
        val observability = InvitationObservability(meterRegistry)

        observability.recordInvitationCreated()
        observability.recordInvitationCreated()
        observability.recordInvitationCreated()
        observability.recordBulkInvite(requested = 5, invited = 3, skipped = 1, failed = 1)

        meterRegistry.find(CREATED_METRIC_NAME).counter()!!.count().shouldBe(3.0)
        counterFor(meterRegistry, "requested")!!.count().shouldBe(5.0)
        counterFor(meterRegistry, "invited")!!.count().shouldBe(3.0)
        counterFor(meterRegistry, "skipped")!!.count().shouldBe(1.0)
        counterFor(meterRegistry, "failed")!!.count().shouldBe(1.0)
    }

    @Test
    fun `records bulk outcomes as aggregate counters without per-value tags`() {
        val meterRegistry = SimpleMeterRegistry()
        val observability = InvitationObservability(meterRegistry)

        observability.recordBulkInvite(requested = 5, invited = 3, skipped = 1, failed = 1)
        observability.recordBulkInvite(requested = 2, invited = 2, skipped = 0, failed = 0)

        counterFor(meterRegistry, "requested")!!.count().shouldBe(7.0)
        counterFor(meterRegistry, "invited")!!.count().shouldBe(5.0)
        counterFor(meterRegistry, "skipped")!!.count().shouldBe(1.0)
        counterFor(meterRegistry, "failed")!!.count().shouldBe(1.0)
        val tagValues = meterRegistry.meters
            .flatMap { meter -> meter.id.tags }
            .map { tag -> tag.value }
        tagValues.none { value -> value.toIntOrNull() != null }.shouldBe(true)
    }

    private fun counterFor(meterRegistry: SimpleMeterRegistry, outcome: String) =
        meterRegistry.find(BULK_METRIC_NAME).tag("outcome", outcome).counter()

    private companion object {
        const val ACCEPTED_METRIC_NAME = "platform.invitations.accepted"
        const val BULK_METRIC_NAME = "platform.waitlist.invitations.bulk"
        const val CREATED_METRIC_NAME = "platform.invitations.created"
        const val EXPIRED_METRIC_NAME = "platform.invitations.expired"
        const val REPLAY_REJECTED_METRIC_NAME = "platform.invitations.replay_rejected"
        const val REVOKED_METRIC_NAME = "platform.invitations.revoked"
    }
}
