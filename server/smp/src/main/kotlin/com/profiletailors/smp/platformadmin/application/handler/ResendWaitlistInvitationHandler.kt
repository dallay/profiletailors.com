package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.smp.platformadmin.application.command.ResendWaitlistInvitationCommand
import com.profiletailors.smp.platformadmin.application.model.AdminInvitationSummary
import com.profiletailors.smp.platformadmin.application.ports.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.ports.TokenHasher
import com.profiletailors.smp.platformadmin.application.ports.WaitlistInvitationRepository
import com.profiletailors.smp.platformadmin.domain.AdminAuditAction
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import com.profiletailors.smp.platformadmin.domain.AdminAuditResult
import com.profiletailors.smp.platformadmin.domain.InvitationDeliveryStatus
import com.profiletailors.smp.platformadmin.domain.InvitationNotFoundException
import com.profiletailors.smp.platformadmin.domain.InvitationNotResendableException
import com.profiletailors.smp.platformadmin.domain.InvitationRateLimitExceededException
import com.profiletailors.smp.platformadmin.domain.InvitationTokenGenerator
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitation
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitationId
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitationStatus
import com.profiletailors.smp.platformadmin.domain.effectivePermissions
import java.time.Clock
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.UUID

open class ResendWaitlistInvitationHandler(
    private val invitationRepository: WaitlistInvitationRepository,
    private val auditPublisher: AdministrativeAuditPublisher,
    private val clock: Clock,
    private val invitationTtl: Duration,
    private val resendLimit: Int,
    private val resendWindowHours: Int,
    private val tokenHasher: TokenHasher,
) {

    @Suppress("ThrowsCount")
    suspend fun handle(command: ResendWaitlistInvitationCommand): AdminInvitationSummary {
        val permissions = command.operatorRoles.effectivePermissions()
        if (PlatformPermission.INVITATIONS_RESEND !in permissions) {
            throw PlatformAccessDeniedException(PlatformPermission.INVITATIONS_RESEND)
        }

        val existing = invitationRepository.findById(WaitlistInvitationId(command.invitationId))
            ?: throw InvitationNotFoundException(command.invitationId.toString())

        val now = clock.instant()
        if (!existing.isActive(now)) throw InvitationNotResendableException(command.invitationId.toString())

        val windowStart = now.minus(resendWindowHours.toLong(), ChronoUnit.HOURS)
        val recentResends = invitationRepository.countResendsSince(
            existing.waitlistEntryId,
            windowStart.toEpochMilli(),
        )
        if (recentResends >= resendLimit) {
            throw InvitationRateLimitExceededException(existing.waitlistEntryId.toString())
        }

        invitationRepository.update(existing.supersede())

        val rawToken = InvitationTokenGenerator.generate()
        val tokenHash: String = tokenHasher.hash(rawToken)

        val newInvitation = invitationRepository.save(
            WaitlistInvitation(
                id = WaitlistInvitationId.generate(),
                waitlistEntryId = existing.waitlistEntryId,
                tokenHash = tokenHash,
                status = WaitlistInvitationStatus.ACTIVE,
                issuedAt = now,
                expiresAt = now + invitationTtl,
                createdBy = command.operatorPrincipalId,
                deliveryStatus = InvitationDeliveryStatus.PENDING,
            ),
        )

        auditPublisher.publish(
            AdminAuditEvent(
                eventId = UUID.randomUUID(),
                occurredAt = now,
                operatorPrincipalId = command.operatorPrincipalId,
                operatorPlatformRoles = command.operatorRoles,
                action = AdminAuditAction.INVITATION_RESENT,
                targetType = "WaitlistInvitation",
                targetId = command.invitationId.toString(),
                result = AdminAuditResult.SUCCEEDED,
            ),
        )

        return newInvitation.toSummary()
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
