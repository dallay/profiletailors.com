package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.identity.application.RateLimit
import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.AcceptInvitationCommand
import com.profiletailors.smp.platformadmin.application.AcceptInvitationHandler
import com.profiletailors.smp.platformadmin.application.InvitationAcceptanceResult
import com.profiletailors.smp.platformadmin.domain.InvitationRateLimitExceededException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import java.time.Clock
import java.time.Duration

@RestController
@RequestMapping("/api/invitations")
class InvitationAcceptanceController(
    private val acceptInvitationHandler: AcceptInvitationHandler,
    private val requestContextStore: RequestContextStore,
    private val acceptAttemptRateLimit: RateLimit,
    private val clock: Clock,
) {
    @PostMapping("/accept", consumes = ["application/json"])
    suspend fun accept(
        @RequestBody request: AcceptInvitationRequest,
        exchange: ServerWebExchange,
    ): ResponseEntity<InvitationAcceptanceResult> {
        val principal = requestContextStore.currentPrincipalContext()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (principal.principalType != PrincipalType.USER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        val token = request.token?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return ResponseEntity.badRequest().build()

        if (!admitAttempt(principal.principalId, exchange)) {
            throw InvitationRateLimitExceededException.acceptAttemptThrottled()
        }

        val result = acceptInvitationHandler.handle(
            AcceptInvitationCommand(
                rawToken = token,
                authenticatedPrincipalId = principal.principalId,
                authenticatedEmail = principal.attributes["email"] ?: principal.subject,
            ),
        )
        return ResponseEntity.ok(result)
    }

    private fun admitAttempt(principalId: String, exchange: ServerWebExchange): Boolean {
        val throttleKey = ACCEPT_ATTEMPT_BUCKET + ":$principalId:${clientIp(exchange)}"
        return acceptAttemptRateLimit.tryAcquire(
            throttleKey,
            ACCEPT_ATTEMPT_WINDOW,
            clock.instant(),
            ACCEPT_ATTEMPT_MAX,
        )
    }

    private fun clientIp(exchange: ServerWebExchange): String =
        exchange.request.remoteAddress?.address?.hostAddress ?: UNKNOWN_CLIENT_IP

    companion object {
        private const val ACCEPT_ATTEMPT_BUCKET = "invitation-accept"
        private const val ACCEPT_ATTEMPT_MAX = 10
        private const val UNKNOWN_CLIENT_IP = "unknown"
        private val ACCEPT_ATTEMPT_WINDOW: Duration = Duration.ofMinutes(10)
    }
}

data class AcceptInvitationRequest(val token: String? = null)
