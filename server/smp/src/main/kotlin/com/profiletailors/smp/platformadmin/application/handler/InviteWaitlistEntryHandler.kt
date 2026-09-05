package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryStatus
import com.profiletailors.notifications.domain.event.InvitationCreated
import com.profiletailors.smp.platformadmin.application.command.InviteWaitlistEntryCommand
import com.profiletailors.smp.platformadmin.application.contracts.AcceptUrlTemplate
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.contracts.TokenHasher
import com.profiletailors.smp.platformadmin.application.contracts.WaitlistEntryAdmin
import com.profiletailors.smp.platformadmin.application.contracts.WaitlistInvitationContext
import com.profiletailors.smp.platformadmin.application.contracts.WaitlistInvitationRepository
import com.profiletailors.smp.platformadmin.application.model.AdminInvitationSummary
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
    private val waitlistEntryAdmin: WaitlistEntryAdmin,
    private val invitationRepository: WaitlistInvitationRepository,
    private val auditPublisher: AdministrativeAuditPublisher,
    private val eventPublisher: EventPublisher<DomainEvent>,
    private val clock: Clock,
    private val invitationTtl: Duration,
    private val tokenHasher: TokenHasher,
    private val acceptUrlTemplate: AcceptUrlTemplate,
) {

    @Suppress("ThrowsCount", "LongMethod")
    suspend fun handle(command: InviteWaitlistEntryCommand): AdminInvitationSummary {
        val permissions = command.operatorRoles.effectivePermissions()
        if (PlatformPermission.WAITLIST_INVITE !in permissions) {
            throw PlatformAccessDeniedException(PlatformPermission.WAITLIST_INVITE)
        }

        val entry = waitlistEntryAdmin.findById(command.waitlistEntryId)
            ?: throw WaitlistEntryNotFoundException(command.waitlistEntryId)

        val context: WaitlistInvitationContext = waitlistEntryAdmin.findInvitationContext(command.waitlistEntryId)
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
            waitlistEntryAdmin.save(entry)
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

        eventPublisher.publish(
            InvitationCreated(
                invitationId = invitation.id.value,
                waitlistEntryId = command.waitlistEntryId,
                operatorPrincipalId = command.operatorPrincipalId,
                recipient = context.recipientEmail,
                workspaceName = context.workspaceName,
                acceptUrl = acceptUrlTemplate.build(rawToken),
                locale = context.locale,
                rawToken = rawToken,
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
