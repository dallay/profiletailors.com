package com.profiletailors.smp.identity.infrastructure.http

import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.smp.identity.application.CloseAccountCommand
import com.profiletailors.smp.identity.application.CloseAccountHandler
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * Controller for account lifecycle operations (closure, deactivation, etc.).
 *
 * ## Security
 * Requires authenticated principal context. The principal ID is
 * extracted from the security context via [PrincipalContextProvider].
 *
 * @property closeAccountHandler Orchestrates the account closure flow
 * @property principalContextProvider Provides the authenticated principal
 */
@RestController
@RequestMapping("/api/v1/account")
@Tag(
    name = "Account Lifecycle",
    description = "Account lifecycle management endpoints",
)
class AccountLifecycleController(
    private val closeAccountHandler: CloseAccountHandler,
    private val principalContextProvider: PrincipalContextProvider,
) {

    /**
     * Permanently close the authenticated user's account.
     *
     * This action is irreversible. All data associated with the account
     * will be anonymized or deleted.
     *
     * @param request The close account request containing confirmation text
     * @return 200 OK on success
     * @throws ResponseStatusException 400 if confirmation is invalid
     * @throws ResponseStatusException 429 if rate-limited
     */
    @Operation(
        summary = "Close account",
        description = "Permanently closes the authenticated user's account. " +
            "This action is irreversible and will anonymize or delete all associated data.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Account closed successfully",
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid confirmation text",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded — try again later",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
        ],
    )
    @PostMapping("/close")
    suspend fun closeAccount(@Valid @RequestBody request: CloseAccountRequestDto): ResponseEntity<Unit> {
        val principal = principalContextProvider.require()

        val command = CloseAccountCommand(
            principalId = principal.principalId,
            confirmation = request.confirmation,
        )

        try {
            closeAccountHandler.handle(command)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message, e)
        }

        return ResponseEntity.ok().build()
    }
}

/**
 * Request DTO for account closure.
 */
data class CloseAccountRequestDto(
    @field:NotBlank(message = "Confirmation text is required")
    val confirmation: String,
)
