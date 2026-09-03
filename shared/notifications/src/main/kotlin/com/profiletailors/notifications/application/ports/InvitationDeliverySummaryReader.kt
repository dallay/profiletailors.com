package com.profiletailors.notifications.application.ports

import com.profiletailors.notifications.domain.NotificationStatus
import java.time.Instant
import java.util.UUID

data class InvitationDeliverySummary(
    val count: Int,
    val latestStatus: NotificationStatus?,
    val latestCreatedAt: Instant?,
    val latestSentAt: Instant?,
    val latestFailedAt: Instant?,
) {
    init {
        require(count >= 0) { "Invitation delivery count must not be negative" }
    }

    companion object {
        val EMPTY: InvitationDeliverySummary = InvitationDeliverySummary(
            count = 0,
            latestStatus = null,
            latestCreatedAt = null,
            latestSentAt = null,
            latestFailedAt = null,
        )
    }
}

fun interface InvitationDeliverySummaryReader {
    /**
 * Summarizes notification delivery activity for an invitation.
 *
 * @param invitationId The identifier of the invitation.
 * @return The invitation's delivery summary.
 */
suspend fun summarize(invitationId: UUID): InvitationDeliverySummary
}
