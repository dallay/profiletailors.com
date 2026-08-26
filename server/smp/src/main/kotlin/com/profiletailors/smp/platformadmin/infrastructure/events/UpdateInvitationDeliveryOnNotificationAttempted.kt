package com.profiletailors.smp.platformadmin.infrastructure.events

import com.profiletailors.common.domain.bus.event.EventConsumer
import com.profiletailors.notifications.domain.event.InvitationDeliveryAttempted
import com.profiletailors.smp.platformadmin.application.contracts.WaitlistInvitationRepository
import com.profiletailors.smp.platformadmin.domain.InvitationDeliveryStatus
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitation
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitationId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Bridges [InvitationDeliveryAttempted] (published by the notifications bounded context)
 * to the platform-admin invitation aggregate.
 *
 * The notifications module deliberately does NOT depend on platform-admin; it publishes
 * the outcome as a plain string (`SENT` or `FAILED`) and this consumer is the single place
 * that maps the string back to the platform-admin [InvitationDeliveryStatus] enum and
 * persists the updated invitation row.
 *
 * Idempotent: re-delivering the same event (e.g. bus retry) ends with the same final
 * state because the lookup is by primary key and the update overwrites the previous value.
 */
@Component
internal class UpdateInvitationDeliveryOnNotificationAttempted(
    private val invitationRepository: WaitlistInvitationRepository,
) : EventConsumer<InvitationDeliveryAttempted> {

    private val log = LoggerFactory.getLogger(UpdateInvitationDeliveryOnNotificationAttempted::class.java)

    override suspend fun consume(event: InvitationDeliveryAttempted) {
        val invitationId = WaitlistInvitationId(event.invitationId)
        val existing: WaitlistInvitation = invitationRepository.findById(invitationId) ?: run {
            log.warn(
                "Cannot apply InvitationDeliveryAttempted for invitation '{}' — invitation not found",
                invitationId,
            )
            return
        }
        val newStatus = when (event.status.uppercase()) {
            "SENT" -> {
                InvitationDeliveryStatus.SENT
            }
            "FAILED" -> {
                InvitationDeliveryStatus.FAILED
            }
            else -> {
                log.warn(
                    "Unknown InvitationDeliveryAttempted status '{}' for invitation '{}' — leaving unchanged",
                    event.status,
                    invitationId,
                )
                return
            }
        }
        val updated = existing.copy(deliveryStatus = newStatus)
        invitationRepository.update(updated)
        log.info(
            "Updated deliveryStatus to '{}' for invitation '{}' (was '{}')",
            newStatus,
            invitationId,
            existing.deliveryStatus,
        )
    }
}
