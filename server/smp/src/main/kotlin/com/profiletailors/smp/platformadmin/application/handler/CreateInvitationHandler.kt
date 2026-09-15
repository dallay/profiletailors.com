package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.notifications.domain.InvitationEmail
import com.profiletailors.smp.platformadmin.application.PlatformPrincipalIds
import com.profiletailors.smp.platformadmin.application.command.CreateInvitationCommand
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.contracts.InvitationEventPublisher
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
import com.profiletailors.smp.platformadmin.domain.InvitationTokenGenerator
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.WorkspaceNotFoundException
import com.profiletailors.smp.platformadmin.domain.effectivePermissions
import com.profiletailors.smp.tenancy.application.WorkspaceNameReader
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

class CreateInvitationHandler(
    private val invitationRepository: InvitationRepository,
    private val auditPublisher: AdministrativeAuditPublisher,
    private val eventPublisher: InvitationEventPublisher,
    private val transactionRunner: AtomicTransactionRunner,
    private val workspaceNameReader: WorkspaceNameReader,
    private val clock: Clock,
    private val invitationTtl: Duration,
    private val tokenHasher: TokenHasher,
    private val invitationTokenCandidateKey: InvitationTokenCandidateKey,
    private val telemetry: InvitationTelemetry,
) {
    suspend fun handle(command: CreateInvitationCommand): CreateInvitationResult {
        val permissions = command.operatorRoles.effectivePermissions()
        if (PlatformPermission.INVITATIONS_CREATE !in permissions) {
            throw PlatformAccessDeniedException(PlatformPermission.INVITATIONS_CREATE)
        }

        val result = transactionRunner.runAtomically {
            val (resolvedWorkspaceName, target) = resolveTarget(command)
            val normalizedEmail = command.email.trim().lowercase()
            val now = clock.instant()
            val workspaceId = command.workspaceId
            if (workspaceId != null &&
                invitationRepository.hasActiveInvitationFor(normalizedEmail, workspaceId, now)
            ) {
                throw InvitationAlreadyActiveException("$normalizedEmail in workspace $workspaceId")
            }

            val rawToken = InvitationTokenGenerator.generate()
            val tokenHash: String = tokenHasher.hash(rawToken)
            val candidateKey = invitationTokenCandidateKey.candidateKey(rawToken)

            val invitation = Invitation(
                id = InvitationId.generate(),
                source = InvitationSource.DIRECT,
                sourceReferenceId = null,
                target = target,
                workspaceId = command.workspaceId,
                invitedEmailNormalized = normalizedEmail,
                tokenHash = tokenHash,
                status = InvitationStatus.ACTIVE,
                issuedBy = PlatformPrincipalIds.fromUuid(command.operatorPrincipalId),
                createdAt = now,
                expiresAt = now + invitationTtl,
            )

            val saved = invitationRepository.save(invitation, candidateKey)
            publishCreatedAudit(now, command, saved.id.value)

            eventPublisher.publish(
                InvitationIssued(
                    invitationId = saved.id.value,
                    recipientEmail = normalizedEmail,
                    workspaceName = resolvedWorkspaceName,
                    target = target,
                    locale = null,
                    rawToken = rawToken,
                    deliveryId = null,
                ),
            )

            CreateInvitationResult(
                invitationId = saved.id.value,
                status = saved.status.name,
                expiresAt = saved.expiresAt.toString(),
                version = saved.version,
            )
        }
        telemetry.recordInvitationCreated()
        return result
    }

    private suspend fun resolveTarget(command: CreateInvitationCommand): Pair<String, InvitationTarget> =
        when (command.target) {
            InvitationTarget.EXISTING_WORKSPACE -> {
                val workspaceId = requireNotNull(command.workspaceId) {
                    "EXISTING_WORKSPACE invitation requires workspaceId"
                }
                val resolvedName = workspaceNameReader.findName(workspaceId)
                    ?: throw WorkspaceNotFoundException(workspaceId)
                resolvedName to InvitationTarget.EXISTING_WORKSPACE
            }
            InvitationTarget.NEW_WORKSPACE -> {
                InvitationEmail.NEW_WORKSPACE_COPY_EN to InvitationTarget.NEW_WORKSPACE
            }
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
