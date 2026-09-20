package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.ConfigurationIdempotencyService
import com.profiletailors.smp.platformadmin.application.OperatorAccess
import com.profiletailors.smp.platformadmin.application.OperatorAccessResolver
import com.profiletailors.smp.platformadmin.application.command.ChangeRegistrationModeCommand
import com.profiletailors.smp.platformadmin.application.handler.RegistrationModeHandlers
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/configuration")
class AdminConfigurationController(
    private val registrationModeHandlers: RegistrationModeHandlers,
    private val operatorAccessResolver: OperatorAccessResolver,
    private val requestContextStore: RequestContextStore,
    private val configurationIdempotencyService: ConfigurationIdempotencyService,
) {
    @GetMapping("/registration-mode")
    suspend fun getRegistrationMode(): ResponseEntity<RegistrationModeResult> {
        val operator = resolveOperator() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val mode = registrationModeHandlers.currentMode(operator.roles)
        return ResponseEntity.ok(RegistrationModeResult(mode.name))
    }

    @PostMapping("/registration-mode")
    suspend fun changeRegistrationMode(
        @RequestBody request: ChangeRegistrationModeRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
    ): ResponseEntity<RegistrationModeResult> {
        requireIdempotencyKey(idempotencyKey)
        val operator = resolveOperator() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val result = configurationIdempotencyService.execute(
            operator.principalId,
            "change_registration_mode",
            idempotencyKey,
            RegistrationModeResult::class.java,
        ) {
            val change = registrationModeHandlers.changeMode(
                ChangeRegistrationModeCommand(operator.principalId, operator.roles, request.mode),
            )
            RegistrationModeResult(change.newMode.name)
        }
        return ResponseEntity.ok(result)
    }

    private fun requireIdempotencyKey(value: String) {
        require(
            value.length in IDEMPOTENCY_KEY_MIN_LENGTH..IDEMPOTENCY_KEY_MAX_LENGTH &&
                value.all { it.isLetterOrDigit() || it in "._:-" },
        )
    }

    private suspend fun resolveOperator(): OperatorAccess? {
        val ctx = requestContextStore.currentPrincipalContext() ?: return null
        return operatorAccessResolver.resolve(ctx)
    }

    private companion object {
        const val IDEMPOTENCY_KEY_MIN_LENGTH = 1
        const val IDEMPOTENCY_KEY_MAX_LENGTH = 128
    }
}

data class RegistrationModeResult(val mode: String)

data class ChangeRegistrationModeRequest(val mode: String)
