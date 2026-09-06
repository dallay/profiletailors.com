package com.profiletailors.smp.identity.infrastructure.http

import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.smp.credentials.application.RefreshSessionProperties
import com.profiletailors.smp.identity.application.AccountSecurityService
import com.profiletailors.smp.identity.application.ChangePasswordCommand
import com.profiletailors.smp.identity.application.SecurityCapabilitiesDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class ChangePasswordRequest(
    @field:NotBlank(message = "Current password is required")
    val currentPassword: String,

    @field:NotBlank(message = "New password is required")
    @field:Size(min = 12, max = 128, message = "Password must be between 12 and 128 characters")
    val newPassword: String,
)

@Validated
@RestController
@RequestMapping("/api/auth/me")
@Tag(name = "Account Security", description = "Account security capabilities and password management")
class AccountSecurityController(
    private val securityService: AccountSecurityService,
    private val principalContextProvider: PrincipalContextProvider,
    private val refreshSessionProperties: RefreshSessionProperties,
) {

    /**
     * Retrieves the security capabilities for the currently authenticated account.
     *
     * @return The account's security capabilities.
     */
    @Operation(
        summary = "Get account security capabilities",
        description = "Returns authentication capability metadata for the currently authenticated user.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @GetMapping("/security", version = "1")
    suspend fun getSecurityCapabilities(): ResponseEntity<SecurityCapabilitiesDto> {
        val principal = principalContextProvider.require()
        val capabilities = securityService.getSecurityCapabilities(principal.principalId)
        return ResponseEntity.ok(capabilities)
    }

    @Operation(
        summary = "Change authenticated user password",
        description = "Changes current password and revokes all other active refresh sessions.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @PostMapping("/change-password", version = "1")
    suspend fun changePassword(
        @Valid @RequestBody request: ChangePasswordRequest,
        serverHttpRequest: ServerHttpRequest,
    ): ResponseEntity<Unit> {
        val principal = principalContextProvider.require()
        val rawRefreshToken = readRefreshCookie(serverHttpRequest)

        securityService.changePassword(
            ChangePasswordCommand(
                principalId = principal.principalId,
                currentPassword = request.currentPassword,
                newPassword = request.newPassword,
                rawRefreshToken = rawRefreshToken,
            ),
        )

        return ResponseEntity.noContent().build()
    }

    /**
         * Reads the configured refresh-session cookie from the request.
         *
         * @param request The HTTP request containing the cookies.
         * @return The refresh-session cookie value, or `null` if the cookie is absent.
         */
        private fun readRefreshCookie(request: ServerHttpRequest): String? =
        request.cookies.getFirst(refreshSessionProperties.cookieName)?.value
}
