package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryStatus
import com.profiletailors.smp.platformadmin.application.command.CancelWaitlistEntryCommand
import com.profiletailors.smp.platformadmin.application.ports.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.ports.WaitlistEntryAdminPort
import com.profiletailors.smp.platformadmin.application.ports.WaitlistInvitationRepository
import com.profiletailors.smp.platformadmin.domain.AdminAuditAction
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import com.profiletailors.smp.platformadmin.domain.AdminAuditResult
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.WaitlistEntryAlreadyCancelledException
import com.profiletailors.smp.platformadmin.domain.WaitlistEntryAlreadyConvertedException
import com.profiletailors.smp.platformadmin.domain.WaitlistEntryNotFoundException
import com.profiletailors.smp.platformadmin.domain.effectivePermissions
import java.time.Clock
import java.util.UUID

open class CancelWaitlistEntryHandler(
    private val waitlistEntryPort: WaitlistEntryAdminPort,
    private val invitationRepository: WaitlistInvitationRepository,
    private val auditPublisher: AdministrativeAuditPublisher,
    private val clock: Clock,
) {
    @Suppress("ThrowsCount")
    suspend fun handle(command: CancelWaitlistEntryCommand) {
        val permissions = command.operatorRoles.effectivePermissions()
        if (PlatformPermission.WAITLIST_CANCEL !in permissions) {
            throw PlatformAccessDeniedException(PlatformPermission.WAITLIST_CANCEL)
        }

        val entry = waitlistEntryPort.findById(command.waitlistEntryId)
            ?: throw WaitlistEntryNotFoundException(command.waitlistEntryId)

        when (entry.status) {
            WaitlistEntryStatus.CONVERTED -> throw WaitlistEntryAlreadyConvertedException(command.waitlistEntryId)
            WaitlistEntryStatus.CANCELLED -> throw WaitlistEntryAlreadyCancelledException(command.waitlistEntryId)
            else -> Unit
        }

        val now = clock.instant()

        val activeInvitation = invitationRepository.findActiveByWaitlistEntryId(command.waitlistEntryId)
        if (activeInvitation != null) {
            invitationRepository.update(activeInvitation.revoke(now, command.operatorPrincipalId))
        }

        entry.cancel(now)
        waitlistEntryPort.save(entry)

        auditPublisher.publish(
            AdminAuditEvent(
                eventId = UUID.randomUUID(),
                occurredAt = now,
                operatorPrincipalId = command.operatorPrincipalId,
                operatorPlatformRoles = command.operatorRoles,
                action = AdminAuditAction.WAITLIST_ENTRY_CANCELLED,
                targetType = "WaitlistEntry",
                targetId = command.waitlistEntryId,
                result = AdminAuditResult.SUCCEEDED,
                reason = command.reason,
            ),
        )
    }
}
