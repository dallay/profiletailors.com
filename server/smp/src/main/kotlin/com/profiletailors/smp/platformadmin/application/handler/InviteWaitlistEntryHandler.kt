package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryStatus
import com.profiletailors.smp.platformadmin.application.command.InviteWaitlistEntryCommand
import com.profiletailors.smp.platformadmin.application.model.AdminInvitationSummary
import com.profiletailors.smp.platformadmin.application.ports.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.ports.TokenHasher
import com.profiletailors.smp.platformadmin.application.ports.WaitlistEntryAdminPort
import com.profiletailors.smp.platformadmin.application.ports.WaitlistInvitationRepository
import com.profiletailors.smp.platformadmin.domain.AdminAuditAction
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import com.profiletailors.smp.platformadmin.domain.AdminAuditResult
import com.profiletailors.smp.platformadmin.domain.InvitationAlreadyActiveException
import com.profiletailors.smp.platformadmin.domain.InvitationDeliveryStatus
import com.profiletailors.smp.platformadmin.domain.InvitationTokenGenerator
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.WaitlistEntryAlreadyConvertedException
import com.profiletailors.smp.platformadmin.domain.WaitlistEntryNotFoundException
import com.profiletailors.smp.platformadmin.domain.WaitlistEntryNotInvitableException
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitation
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitationId
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitationStatus
import com.profiletailors.smp.platformadmin.domain.effectivePermissions
import java.time.Clock
import java.time.Duration
import java.util.UUID

open class InviteWaitlistEntryHandler(
    private val waitlistEntryPort: WaitlistEntryAdminPort,
    private val invitationRepository: WaitlistInvitationRepository,
    private val auditPublisher: AdministrativeAuditPublisher,
    private val clock: Clock,
    private val invitationTtl: Duration,
    private val tokenHasher: TokenHasher,
) {

    @Suppress("ThrowsCount")
    suspend fun handle(command: InviteWaitlistEntryCommand): AdminInvitationSummary {
        val permissions = command.operatorRoles.effectivePermissions()
        if (PlatformPermission.WAITLIST_INVITE !in permissions) {
            throw PlatformAccessDeniedException(PlatformPermission.WAITLIST_INVITE)
        }

        val entry = waitlistEntryPort.findById(command.waitlistEntryId)
            ?: throw WaitlistEntryNotFoundException(command.waitlistEntryId)

        when (entry.status) {
            WaitlistEntryStatus.CONVERTED ->
                throw WaitlistEntryAlreadyConvertedException(command.waitlistEntryId)
            WaitlistEntryStatus.CANCELLED ->
                throw WaitlistEntryNotInvitableException(command.waitlistEntryId, "Entry is cancelled")
            WaitlistEntryStatus.INVITED -> {
                val existing = invitationRepository.findActiveByWaitlistEntryId(command.waitlistEntryId)
                    ?: throw WaitlistEntryNotInvitableException(
                        command.waitlistEntryId,
                        "No active invitation to supersede",
                    )
                invitationRepository.update(existing.supersede())
            }
            WaitlistEntryStatus.PENDING -> {
                val existing = invitationRepository.findActiveByWaitlistEntryId(command.waitlistEntryId)
                if (existing != null) throw InvitationAlreadyActiveException(command.waitlistEntryId)
            }
        }

        val now = clock.instant()
        val rawToken = InvitationTokenGenerator.generate()
        val tokenHash: String = tokenHasher.hash(rawToken)

        val invitation = invitationRepository.save(
            WaitlistInvitation(
                id = WaitlistInvitationId.generate(),
                waitlistEntryId = command.waitlistEntryId,
                tokenHash = tokenHash,
                status = WaitlistInvitationStatus.ACTIVE,
                issuedAt = now,
                expiresAt = now + invitationTtl,
                createdBy = command.operatorPrincipalId,
                deliveryStatus = InvitationDeliveryStatus.PENDING,
            ),
        )

        if (entry.status == WaitlistEntryStatus.PENDING) {
            entry.invite(now)
            waitlistEntryPort.save(entry)
        }

        auditPublisher.publish(
            AdminAuditEvent(
                eventId = UUID.randomUUID(),
                occurredAt = now,
                operatorPrincipalId = command.operatorPrincipalId,
                operatorPlatformRoles = command.operatorRoles,
                action = AdminAuditAction.WAITLIST_ENTRY_INVITED,
                targetType = "WaitlistEntry",
                targetId = command.waitlistEntryId,
                result = AdminAuditResult.SUCCEEDED,
            ),
        )

        return invitation.toSummary()
    }

    private fun WaitlistInvitation.toSummary() = AdminInvitationSummary(
        id = id.value,
        waitlistEntryId = waitlistEntryId,
        status = status.name,
        issuedAt = issuedAt,
        expiresAt = expiresAt,
        acceptedAt = acceptedAt,
        revokedAt = revokedAt,
        revokedBy = revokedBy,
        createdBy = createdBy,
        deliveryStatus = deliveryStatus.name,
        deliveryAttemptCount = deliveryAttemptCount,
        version = version,
    )
}
