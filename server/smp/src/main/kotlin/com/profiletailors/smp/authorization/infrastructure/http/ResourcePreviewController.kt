package com.profiletailors.smp.authorization.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.authorization.application.resource.getpreview.GetResourcePreviewQuery
import com.profiletailors.smp.authorization.application.resource.getpreview.ResourcePreview
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ProblemDetail
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Controller for resource preview retrieval.
 *
 * This controller provides access to resource preview information, allowing users to
 * view basic metadata and access information about resources before performing actions.
 * Useful for displaying resource cards, tooltips, or permission checks in the UI.
 *
 * Uses media-type versioning via Accept header: application/vnd.api.v1+json
 *
 * ## Security Features:
 * - Requires authentication (Bearer JWT token)
 * - Requires workspace context (X-Workspace-Id header)
 * - Returns preview only for resources the user has permission to view
 *
 * @property mediator The mediator for dispatching queries.
 * @created 2026-05-24
 */
@Validated
@RestController
@RequestMapping(value = ["/api/authorization/resources"])
@Tag(
    name = "Resource Preview",
    description = "Resource preview and metadata endpoints",
)
class ResourcePreviewController(
    private val mediator: Mediator,
) {

    /**
     * Get resource preview by ID.
     *
     * Retrieves preview information for a specific resource including metadata, access rights,
     * and basic properties. The preview includes only information the authenticated user has
     * permission to view.
     *
     * @param resourceId The unique identifier of the resource.
     * @return ResourcePreview containing the resource metadata and access information.
     */
    @Operation(
        summary = "Get resource preview by ID",
        description = "Retrieves preview information for a specific resource including metadata, " +
            "access rights, and basic properties. The preview includes only information the " +
            "authenticated user has permission to view. Requires a valid JWT access token and " +
            "X-Workspace-Id header.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Resource preview retrieved successfully",
                content = [Content(schema = Schema(implementation = ResourcePreview::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Unauthorized - Missing or invalid authentication token",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "403",
                description = "Forbidden - User does not have permission to view this resource",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Resource not found or not accessible in current workspace",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error during resource preview retrieval",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
        ],
    )
    @GetMapping("/{resourceId}/preview", version = "1")
    suspend fun getResourcePreview(
        @Parameter(
            description = "The unique identifier of the resource",
            required = true,
            example = "res_abc123xyz",
        )
        @PathVariable resourceId: String,
    ): ResourcePreview = mediator.send(GetResourcePreviewQuery(resourceId))
}
