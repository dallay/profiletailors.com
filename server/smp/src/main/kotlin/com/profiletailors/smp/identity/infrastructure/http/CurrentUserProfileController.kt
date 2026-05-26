package com.profiletailors.smp.identity.infrastructure.http

import com.profiletailors.smp.identity.application.CurrentUserProfile
import com.profiletailors.smp.identity.application.GetCurrentUserProfileService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ProblemDetail
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Controller for retrieving the current authenticated user's profile.
 *
 * This controller provides access to the authenticated user's profile information
 * based on the JWT token in the Authorization header.
 *
 * Uses media-type versioning via Accept header: application/vnd.api.v1+json
 *
 * ## Security Features:
 * - Requires authentication (Bearer JWT token)
 * - Returns profile data for the authenticated user only
 *
 * @property service The service for retrieving current user profile.
 * @created 2026-05-24
 */
@Validated
@RestController
@RequestMapping(value = ["/api/auth"])
@Tag(
    name = "User Profile",
    description = "Current user profile endpoints",
)
class CurrentUserProfileController(
    private val service: GetCurrentUserProfileService,
) {

    /**
     * Get current authenticated user's profile.
     *
     * Returns the profile information for the currently authenticated user based on
     * the JWT token provided in the Authorization header.
     *
     * @return CurrentUserProfile containing user information.
     */
    @Operation(
        summary = "Get current user profile",
        description = "Returns the profile information for the currently authenticated user. " +
            "Requires a valid JWT access token in the Authorization header.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "User profile retrieved successfully",
                content = [Content(schema = Schema(implementation = CurrentUserProfile::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Unauthorized - Missing or invalid authentication token",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error during profile retrieval",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
        ],
    )
    @GetMapping("/me", version = "1")
    suspend fun currentUser(): CurrentUserProfile = service.execute()
}
