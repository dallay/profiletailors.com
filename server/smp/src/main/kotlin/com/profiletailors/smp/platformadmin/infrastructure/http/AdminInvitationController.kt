package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.OperatorAccessResolver
import com.profiletailors.smp.platformadmin.application.command.CreateInvitationCommand
import com.profiletailors.smp.platformadmin.application.command.ResendInvitationCommand
import com.profiletailors.smp.platformadmin.application.command.RevokeInvitationCommand
import com.profiletailors.smp.platformadmin.application.contracts.AdminInvitationQuery
import com.profiletailors.smp.platformadmin.application.handler.CreateInvitationHandler
import com.profiletailors.smp.platformadmin.application.handler.ResendInvitationHandler
import com.profiletailors.smp.platformadmin.application.handler.ResendWaitlistInvitationHandler
import com.profiletailors.smp.platformadmin.application.handler.RevokeInvitationHandler
import com.profiletailors.smp.platformadmin.application.handler.RevokeWaitlistInvitationHandler
import com.profiletailors.smp.platformadmin.application.model.AdminInvitationSummary
import com.profiletailors.smp.platformadmin.application.result.CreateInvitationResult
import com.profiletailors.smp.platformadmin.application.result.ResendInvitationResult
import com.profiletailors.smp.platformadmin.application.result.RevokeInvitationResult
import com.profiletailors.smp.platformadmin.domain.InvitationTarget
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.effectivePermissions
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/admin/invitations")
class AdminInvitationController(
    private val invitationQuery: AdminInvitationQuery,
    private val createInvitationHandler: CreateInvitationHandler,
    private val revokeInvitationHandler: RevokeInvitationHandler,
    private val resendInvitationHandler: ResendInvitationHandler,
    private val resendWaitlistHandler: ResendWaitlistInvitationHandler,
    private val revokeWaitlistHandler: RevokeWaitlistInvitationHandler,
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
    suspend fun resendWaitlist(@PathVariable invitationId: UUID): ResponseEntity<AdminInvitationSummary> {
        val operator = resolveOperator()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val result = resendWaitlistHandler.handle(
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
    suspend fun revokeWaitlist(@PathVariable invitationId: UUID): ResponseEntity<Map<String, String>> {
        val operator = resolveOperator()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        revokeWaitlistHandler.handle(
            com.profiletailors.smp.platformadmin.application.command.RevokeWaitlistInvitationCommand(
                operatorPrincipalId = operator.principalId,
                operatorRoles = operator.roles,
                invitationId = invitationId,
            ),
        )
        return ResponseEntity.ok(mapOf("status" to "revoked"))
    }

    @PostMapping("/direct")
    @Transactional
    suspend fun createDirectInvitation(
        @RequestBody request: CreateDirectInvitationRequest,
    ): ResponseEntity<CreateInvitationResult> {
        val operator = resolveOperator()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val result = createInvitationHandler.handle(
            CreateInvitationCommand(
                operatorPrincipalId = operator.principalId,
                operatorRoles = operator.roles,
                email = request.email,
                target = request.target,
                workspaceId = request.workspaceId,
            ),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(result)
    }

    @PostMapping("/{invitationId}/direct-revoke")
    @Transactional
    suspend fun revokeDirectInvitation(
        @PathVariable invitationId: UUID,
        @RequestBody body: RevokeDirectInvitationRequest,
    ): ResponseEntity<RevokeInvitationResult> {
        val operator = resolveOperator()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        revokeInvitationHandler.handle(
            RevokeInvitationCommand(
                operatorPrincipalId = operator.principalId,
                operatorRoles = operator.roles,
                invitationId = invitationId,
                expectedVersion = body.expectedVersion,
            ),
        )
        return ResponseEntity.ok(RevokeInvitationResult(invitationId = invitationId))
    }

    @PostMapping("/{invitationId}/direct-resend")
    @Transactional
    suspend fun resendDirectInvitation(@PathVariable invitationId: UUID): ResponseEntity<ResendInvitationResult> {
        val operator = resolveOperator()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val result = resendInvitationHandler.handle(
            ResendInvitationCommand(
                operatorPrincipalId = operator.principalId,
                operatorRoles = operator.roles,
                invitationId = invitationId,
            ),
        )
        return ResponseEntity.ok(result)
    }

    private suspend fun resolveOperator(): com.profiletailors.smp.platformadmin.application.OperatorAccess? {
        val ctx = requestContextStore.currentPrincipalContext() ?: return null
        return operatorAccessResolver.resolve(ctx)
    }
}

data class CreateDirectInvitationRequest(val email: String, val target: InvitationTarget, val workspaceId: String?)

data class RevokeDirectInvitationRequest(val expectedVersion: Long)
