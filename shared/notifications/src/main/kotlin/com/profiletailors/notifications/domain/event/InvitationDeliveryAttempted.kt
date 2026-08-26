package com.profiletailors.notifications.domain.event

import com.profiletailors.common.domain.bus.event.BaseDomainEvent
import java.util.UUID

/**
 * Outcome of a single attempt to deliver an invitation email.
 *
 * Published by [com.profiletailors.smp.notifications.infrastructure.email.SendInvitationEmailConsumer]
 * once the email dispatcher returns (success or failure). Consumed by the platform-admin
 * bounded context to update the invitation's [com.profiletailors.smp.platformadmin.domain.InvitationDeliveryStatus].
 *
 * @property invitationId canonical invitation identifier whose delivery state changed
 * @property status textual outcome: `SENT` or `FAILED`. The notification module does not
 *                 understand the platform-admin enum; the platform-admin consumer maps
 *                 this string to its own enum.
 */
data class InvitationDeliveryAttempted(val invitationId: UUID, val status: String) : BaseDomainEvent()
