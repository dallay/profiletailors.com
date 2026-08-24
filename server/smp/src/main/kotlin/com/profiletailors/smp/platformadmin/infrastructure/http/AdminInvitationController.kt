package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.OperatorAccessResolver
import com.profiletailors.smp.platformadmin.application.handler.ResendWaitlistInvitationHandler
import com.profiletailors.smp.platformadmin.application.handler.RevokeWaitlistInvitationHandler
import com.profiletailors.smp.platformadmin.application.model.AdminInvitationSummary
import com.profiletailors.smp.platformadmin.application.ports.AdminInvitationQuery
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
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
    private val invitationQuery: AdminInvitationQuery,
    private val resendHandler: ResendWaitlistInvitationHandler,
    private val revokeHandler: RevokeWaitlistInvitationHandler,
    private val operatorAccessResolver: OperatorAccessResolver,
    private val requestContextStore: RequestContextStore,
) {
    @GetMapping("/{invitationId}")
    suspend fun getInvitation(@PathVariable invitationId: UUID): ResponseEntity<AdminInvitationSummary> {
        val operator = resolveOperator() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (PlatformPermission.INVITATIONS_READ !in operator.roles.effectivePermissions()) {
            throw PlatformAccessDeniedException(PlatformPermission.INVITATIONS_READ)
        }
        val invitation = invitationQuery.findById(invitationId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(invitation)
    }

    @PostMapping("/{invitationId}/resend")
    @Transactional
    suspend fun resend(@PathVariable invitationId: UUID): ResponseEntity<AdminInvitationSummary> {
        val operator = resolveOperator()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val result = resendHandler.handle(
            com.profiletailors.smp.platformadmin.application.command.ResendWaitlistInvitationCommand(
                operatorPrincipalId = operator.principalId,
                operatorRoles = operator.roles,
                invitationId = invitationId,
            ),
        )
        return ResponseEntity.ok(result)
    }

    @PostMapping("/{invitationId}/revoke")
    @Transactional
    suspend fun revoke(@PathVariable invitationId: UUID): ResponseEntity<Map<String, String>> {
        val operator = resolveOperator()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        revokeHandler.handle(
            com.profiletailors.smp.platformadmin.application.command.RevokeWaitlistInvitationCommand(
                operatorPrincipalId = operator.principalId,
                operatorRoles = operator.roles,
                invitationId = invitationId,
            ),
        )
        return ResponseEntity.ok(mapOf("status" to "revoked"))
    }

    private suspend fun resolveOperator(): com.profiletailors.smp.platformadmin.application.OperatorAccess? {
        val ctx = requestContextStore.currentPrincipalContext() ?: return null
        return operatorAccessResolver.resolve(ctx)
    }
}
