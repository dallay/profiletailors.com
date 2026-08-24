package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.platform.domain.RequestContextStore
import com.profiletailors.smp.platformadmin.application.contracts.PlatformRoleAssignmentRepository
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.effectivePermissions
import com.profiletailors.smp.publishing.application.ListStaleJobsQuery
import com.profiletailors.smp.publishing.application.StaleJobsResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.util.UUID

/**
 * Admin-only operator view of stale publication-job claims.
 *
 * The endpoint dispatches [ListStaleJobsQuery] through the [Mediator] bus so the
 * data shape stays safe: every field is structural and PII-free, and the
 * canonical `suggestedAction = "RELEASE_AND_RETRY"` literal is enforced by the
 * application-layer handler.
 *
 * Authorization follows the same pattern as the platform-admin controllers:
 * 401 when no principal context is present, 403 when the operator lacks the
 * `PUBLISHING_STALE_READ` permission. There is no per-workspace scope because
 * this is a global operator view across every workspace.
 */
@RestController
@RequestMapping("/api/admin/publishing")
@Tag(
    name = "Publishing Admin",
    description = "Platform-operator endpoints for stale publication-job visibility",
)
class PublishingStaleJobsController(
    private val mediator: Mediator,
    private val roleAssignmentRepository: PlatformRoleAssignmentRepository,
    private val requestContextStore: RequestContextStore,
) {

    @Operation(
        summary = "List publication-job claims whose lease has expired past the stale threshold",
        description = "Surfaces publication, workspace, age, and a safe canonical next action. " +
            "The response shape is structural only (jobId, publicationId, workspaceId, claimedByWorker, " +
            "claimedAt, leaseExpiresAt, ageSeconds, attemptNumber, suggestedAction). No raw exceptions, " +
            "tokens, provider payloads, or storage paths are exposed.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Stale publication-job claims returned",
                content = [Content(schema = Schema(implementation = StaleJobsResponse::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid stale threshold or limit",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Authentication is required",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "403",
                description = "The principal lacks PUBLISHING_STALE_READ",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
        ],
    )
    @GetMapping("/stale-jobs", version = "1")
    suspend fun listStaleJobs(
        @RequestParam(defaultValue = DEFAULT_LEASE_STALE_THRESHOLD) leaseStaleThreshold: String,
        @RequestParam(defaultValue = DEFAULT_LIMIT) limit: Int,
    ): ResponseEntity<StaleJobsResponse> {
        val ctx = requestContextStore.currentPrincipalContext()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val operatorId = runCatching { UUID.fromString(ctx.principalId) }
            .getOrElse {
                throw PlatformAccessDeniedException(PlatformPermission.PUBLISHING_STALE_READ)
            }
        val assignments = roleAssignmentRepository.findActiveByPrincipalId(operatorId)
        val operatorRoles = assignments.map { it.role }.toSet()

        if (PlatformPermission.PUBLISHING_STALE_READ !in operatorRoles.effectivePermissions()) {
            throw PlatformAccessDeniedException(PlatformPermission.PUBLISHING_STALE_READ)
        }

        val threshold = parseThreshold(leaseStaleThreshold)
        requireValidLimit(limit)

        val response = mediator.send(
            ListStaleJobsQuery(leaseStaleThreshold = threshold, limit = limit),
        )
        return ResponseEntity.ok(response)
    }

    private fun parseThreshold(raw: String): Duration {
        val duration = runCatching { Duration.parse(raw) }
            .getOrElse { throw IllegalArgumentException(INVALID_THRESHOLD_MESSAGE) }
        if (duration.isZero || duration.isNegative) {
            throw IllegalArgumentException(INVALID_THRESHOLD_MESSAGE)
        }
        return duration
    }

    private fun requireValidLimit(limit: Int) {
        if (limit !in MIN_LIMIT..MAX_LIMIT) {
            throw IllegalArgumentException("limit must be between $MIN_LIMIT and $MAX_LIMIT.")
        }
    }

    private companion object {
        const val DEFAULT_LEASE_STALE_THRESHOLD = "PT5M"
        const val MIN_LIMIT = 1
        const val MAX_LIMIT = 100
        const val DEFAULT_LIMIT = "50"
        const val INVALID_THRESHOLD_MESSAGE =
            "leaseStaleThreshold must be a positive ISO-8601 duration."
    }
}
