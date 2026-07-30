package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.handler.ResendWaitlistInvitationHandler
import com.profiletailors.smp.platformadmin.application.handler.RevokeWaitlistInvitationHandler
import com.profiletailors.smp.platformadmin.application.model.AdminInvitationSummary
import com.profiletailors.smp.platformadmin.application.ports.PlatformRoleAssignmentRepository
import com.profiletailors.smp.platformadmin.application.ports.WaitlistInvitationRepository
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitationId
import com.profiletailors.smp.platformadmin.domain.effectivePermissions
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/admin/invitations")
class AdminInvitationController(
    private val invitationRepository: WaitlistInvitationRepository,
    private val resendHandler: ResendWaitlistInvitationHandler,
    private val revokeHandler: RevokeWaitlistInvitationHandler,
    private val roleAssignmentRepository: PlatformRoleAssignmentRepository,
    private val requestContextStore: RequestContextStore,
) {
    @GetMapping("/{invitationId}")
    suspend fun getInvitation(@PathVariable invitationId: UUID): ResponseEntity<AdminInvitationSummary> {
        val (_, operatorRoles) = resolveOperator() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (PlatformPermission.INVITATIONS_READ !in operatorRoles.effectivePermissions()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        val invitation = invitationRepository.findById(WaitlistInvitationId(invitationId))
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(invitation.toSummary())
    }

    @PostMapping("/{invitationId}/resend")
    @Transactional
    suspend fun resend(@PathVariable invitationId: UUID): ResponseEntity<AdminInvitationSummary> {
        val (operatorId, operatorRoles) = resolveOperator()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val result = resendHandler.handle(
            com.profiletailors.smp.platformadmin.application.command.ResendWaitlistInvitationCommand(
                operatorPrincipalId = operatorId,
                operatorRoles = operatorRoles,
                invitationId = invitationId,
            ),
        )
        return ResponseEntity.ok(result)
    }

    @PostMapping("/{invitationId}/revoke")
    @Transactional
    suspend fun revoke(@PathVariable invitationId: UUID): ResponseEntity<Unit> {
        val (operatorId, operatorRoles) = resolveOperator()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        revokeHandler.handle(
            com.profiletailors.smp.platformadmin.application.command.RevokeWaitlistInvitationCommand(
                operatorPrincipalId = operatorId,
                operatorRoles = operatorRoles,
                invitationId = invitationId,
            ),
        )
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build()
    }

    private suspend fun resolveOperator(): Pair<UUID, Set<PlatformRole>>? {
        val ctx = requestContextStore.currentPrincipalContext() ?: return null
        val operatorId = UUID.fromString(ctx.principalId)
        val assignments = roleAssignmentRepository.findActiveByPrincipalId(operatorId)
        return operatorId to assignments.map { it.role }.toSet()
    }

    private fun com.profiletailors.smp.platformadmin.domain.WaitlistInvitation.toSummary() = AdminInvitationSummary(
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
