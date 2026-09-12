package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.smp.platformadmin.application.command.CreateInvitationCommand
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.contracts.InvitationRepository
import com.profiletailors.smp.platformadmin.application.contracts.InvitationTelemetry
import com.profiletailors.smp.platformadmin.application.contracts.InvitationTokenCandidateKey
import com.profiletailors.smp.platformadmin.application.contracts.TokenHasher
import com.profiletailors.smp.platformadmin.application.result.CreateInvitationResult
import com.profiletailors.smp.platformadmin.domain.AdminAuditAction
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import com.profiletailors.smp.platformadmin.domain.AdminAuditResult
import com.profiletailors.smp.platformadmin.domain.Invitation
import com.profiletailors.smp.platformadmin.domain.InvitationAlreadyActiveException
import com.profiletailors.smp.platformadmin.domain.InvitationId
import com.profiletailors.smp.platformadmin.domain.InvitationIssued
import com.profiletailors.smp.platformadmin.domain.InvitationSource
import com.profiletailors.smp.platformadmin.domain.InvitationStatus
import com.profiletailors.smp.platformadmin.domain.InvitationTarget
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.effectivePermissions
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

class CreateInvitationHandler(
    private val invitationRepository: InvitationRepository,
    private val auditPublisher: AdministrativeAuditPublisher,
    private val eventPublisher: EventPublisher<DomainEvent>,
    private val clock: Clock,
    private val invitationTtl: Duration,
    private val tokenHasher: TokenHasher,
    private val telemetry: InvitationTelemetry,
) {
    suspend fun handle(command: CreateInvitationCommand): CreateInvitationResult {
        val permissions = command.operatorRoles.effectivePermissions()
        if (PlatformPermission.INVITATIONS_CREATE !in permissions) {
            throw PlatformAccessDeniedException(PlatformPermission.INVITATIONS_CREATE)
        }

        val workspaceId = command.workspaceId
        if (command.target == InvitationTarget.EXISTING_WORKSPACE) {
            require(!workspaceId.isNullOrBlank()) { "EXISTING_WORKSPACE invitation requires workspaceId" }
        }

        val normalizedEmail = command.email.trim().lowercase()
        val now = clock.instant()
        if (workspaceId != null &&
            invitationRepository.hasActiveInvitationFor(normalizedEmail, workspaceId, now)
        ) {
            throw InvitationAlreadyActiveException("$normalizedEmail in workspace $workspaceId")
        }

        val rawToken = com.profiletailors.smp.platformadmin.domain.InvitationTokenGenerator.generate()
        val tokenHash: String = tokenHasher.hash(rawToken)
        val candidateKey: String = (tokenHasher as? InvitationTokenCandidateKey)
            ?.candidateKey(rawToken)
            ?: throw IllegalStateException("TokenHasher must implement InvitationTokenCandidateKey")

        val invitation = Invitation(
            id = InvitationId.generate(),
            source = InvitationSource.DIRECT,
            sourceReferenceId = null,
            target = command.target,
            workspaceId = command.workspaceId,
            invitedEmailNormalized = normalizedEmail,
            tokenHash = tokenHash,
            status = InvitationStatus.ACTIVE,
            issuedBy = command.operatorPrincipalId.toString(),
            createdAt = now,
            expiresAt = now + invitationTtl,
        )

        val saved = invitationRepository.save(invitation, candidateKey)
        telemetry.recordInvitationCreated()
        publishCreatedAudit(now, command, saved.id.value)

        eventPublisher.publish(
            InvitationIssued(
                invitationId = saved.id.value,
                recipientEmail = normalizedEmail,
                workspaceName = command.workspaceId ?: "",
                locale = null,
                rawToken = rawToken,
            ),
        )

        return CreateInvitationResult(
            invitationId = saved.id.value,
            status = saved.status.name,
            expiresAt = saved.expiresAt.toString(),
            version = saved.version,
        )
    }

    private suspend fun publishCreatedAudit(now: Instant, command: CreateInvitationCommand, invitationId: UUID) {
        auditPublisher.publish(
            AdminAuditEvent(
                eventId = UUID.randomUUID(),
                occurredAt = now,
                operatorPrincipalId = command.operatorPrincipalId,
                operatorPlatformRoles = command.operatorRoles,
                action = AdminAuditAction.INVITATION_CREATED,
                targetType = "Invitation",
                targetId = invitationId.toString(),
                result = AdminAuditResult.SUCCEEDED,
            ),
        )
    }
}
