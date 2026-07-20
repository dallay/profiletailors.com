package com.profiletailors.smp.privacy.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.smp.privacy.application.CheckRequestStatusQuery
import com.profiletailors.smp.privacy.application.CorrectionField
import com.profiletailors.smp.privacy.application.DataSubjectRequestResponse
import com.profiletailors.smp.privacy.application.ListRequestsQuery
import com.profiletailors.smp.privacy.application.SubmitAccessRequestCommand
import com.profiletailors.smp.privacy.application.SubmitCorrectionRequestCommand
import com.profiletailors.smp.privacy.application.SubmitDeletionRequestCommand
import com.profiletailors.smp.privacy.application.SubmitExportRequestCommand
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * REST controller for Data Subject Access Request (DSAR) management.
 *
 * Provides endpoints to submit, list, and check the status of privacy
 * requests including ACCESS, EXPORT, CORRECTION, and DELETION under
 * GDPR/CCPA.
 *
 * ## Rate Limiting
 * Maximum 3 DSAR requests per user per calendar day (UTC). Controlled
 * by [RateLimiter].
 *
 * ## Security
 * Requires authenticated principal context. The requester identity is
 * extracted from the security context via [PrincipalContextProvider].
 *
 * @property mediator Command/query mediator for dispatching requests
 * @property principalContextProvider Provides the authenticated principal
 * @property rateLimiter Enforces daily request limits
 */
@RestController
@RequestMapping("/api/v1/privacy/requests")
@Tag(name = "Privacy DSAR", description = "Data Subject Access Request endpoints")
class PrivacyController(
    private val mediator: Mediator,
    private val principalContextProvider: PrincipalContextProvider,
    private val rateLimiter: RateLimiter,
) {

    /**
     * Submit a new DSAR request.
     *
     * The [SubmitPrivacyRequestDto.type] determines which handler is invoked.
     * Rate limiting applies per authenticated principal per calendar day (max 3).
     *
     * @param request The request body with type, notes, and optional correction fields
     * @return The created request summary with status
     * @throws ResponseStatusException 429 if rate-limited, 400 if invalid type
     */
    @Operation(
        summary = "Submit a DSAR request",
        description = "Creates a new data subject access request of the specified type",
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun submitRequest(@Valid @RequestBody request: SubmitPrivacyRequestDto): SubmitPrivacyResponseDto {
        val principal = principalContextProvider.require()

        if (!rateLimiter.tryAcquire(principal.principalId)) {
            throw ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "rate_limit_exceeded: maximum 3 requests per day",
            )
        }

        val command = buildCommand(request, principal.principalId, principal.subject)
        val result: DataSubjectRequestResponse = mediator.send(command)
        return result.toSubmitResponse()
    }

    /**
     * List all DSAR requests for the current principal.
     *
     * Returns requests ordered by most recent first, with pagination metadata.
     *
     * @return Paginated list of requests
     */
    @Operation(
        summary = "List DSAR requests",
        description = "Returns all data subject requests for the current user",
    )
    @GetMapping
    suspend fun listRequests(): PrivacyRequestListResponseDto {
        val principal = principalContextProvider.require()
        val query = ListRequestsQuery(requesterPrincipalId = principal.principalId)
        val results: List<DataSubjectRequestResponse> = mediator.send(query)
        return PrivacyRequestListResponseDto(
            requests = results.map { it.toStatusResponse() },
            total = results.size,
            page = 1,
            perPage = results.size,
        )
    }

    /**
     * Get the status of a single DSAR request by ID.
     *
     * @param id The request identifier
     * @return The request status and details
     * @throws ResponseStatusException 404 if the request is not found
     */
    @Operation(
        summary = "Get DSAR request status",
        description = "Returns the current status and details of a specific request",
    )
    @GetMapping("/{id}")
    suspend fun getRequest(@PathVariable id: String): PrivacyRequestStatusResponseDto {
        val principal = principalContextProvider.require()
        val query = CheckRequestStatusQuery(requestId = id)
        val result: DataSubjectRequestResponse? = mediator.send(query)
        if (result == null || result.requestedBy != principal.principalId) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found: $id")
        }
        return result.toStatusResponse()
    }

    // ——————— Private helpers ———————

    /**
     * Builds the appropriate command based on the request type.
     */
    @Suppress("UNCHECKED_CAST", "BracesOnWhenStatements")
    private fun buildCommand(
        request: SubmitPrivacyRequestDto,
        principalId: String,
        email: String,
    ): CommandWithResult<DataSubjectRequestResponse> = when (request.type.uppercase()) {
        "ACCESS" -> SubmitAccessRequestCommand(
            requestedByPrincipalId = principalId,
            requestedByEmail = email,
            workspaceId = null,
            notes = request.notes,
        )
        "EXPORT" -> SubmitExportRequestCommand(
            requestedByPrincipalId = principalId,
            requestedByEmail = email,
            workspaceId = null,
            notes = request.notes,
        )
        "CORRECTION" -> {
            val field = when {
                !request.newEmail.isNullOrBlank() -> CorrectionField.EMAIL
                !request.newUsername.isNullOrBlank() -> CorrectionField.USERNAME
                else -> throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Either newEmail or newUsername is required for CORRECTION requests",
                )
            }
            SubmitCorrectionRequestCommand(
                requestedByPrincipalId = principalId,
                requestedByEmail = email,
                field = field,
                newValue = request.newEmail ?: request.newUsername!!,
                workspaceId = null,
                notes = request.notes,
            )
        }
        "DELETION" -> SubmitDeletionRequestCommand(
            requestedByPrincipalId = principalId,
            requestedByEmail = email,
            workspaceId = null,
            notes = request.notes,
        )
        else -> throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Invalid request type: ${request.type}. Must be ACCESS, EXPORT, CORRECTION, or DELETION",
        )
    }

    // ——————— Response mapping ———————

    private fun DataSubjectRequestResponse.toSubmitResponse(): SubmitPrivacyResponseDto = SubmitPrivacyResponseDto(
        id = id,
        status = status,
        message = "Request submitted successfully",
        oldValues = null, // Correction old values require additional lookup beyond current scope
        downloadUrl = if (type == "EXPORT") resultRef else null,
    )

    private fun DataSubjectRequestResponse.toStatusResponse(): PrivacyRequestStatusResponseDto =
        PrivacyRequestStatusResponseDto(
            id = id,
            type = type,
            status = status,
            result = toRequestResult(this),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    internal companion object {
        internal fun toRequestResult(response: DataSubjectRequestResponse): PrivacyRequestResult? =
            response.resultRef?.let { PrivacyRequestResult(ref = it) }
    }
}
