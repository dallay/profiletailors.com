package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.AcceptInvitationCommand
import com.profiletailors.smp.platformadmin.application.AcceptInvitationHandler
import com.profiletailors.smp.platformadmin.application.InvitationAcceptanceResult
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/invitations")
class InvitationAcceptanceController(
    private val acceptInvitationHandler: AcceptInvitationHandler,
    private val requestContextStore: RequestContextStore,
) {
    @PostMapping("/accept", consumes = ["application/json"])
    suspend fun accept(@RequestBody request: AcceptInvitationRequest): ResponseEntity<InvitationAcceptanceResult> {
        val principal = requestContextStore.currentPrincipalContext()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val token = request.token?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return ResponseEntity.badRequest().build()

        val result = acceptInvitationHandler.handle(
            AcceptInvitationCommand(
                rawToken = token,
                authenticatedPrincipalId = principal.principalId,
                authenticatedEmail = principal.attributes["email"] ?: principal.subject,
            ),
        )
        return ResponseEntity.ok(result)
    }
}

data class AcceptInvitationRequest(val token: String? = null)
