package com.profiletailors.smp.identity.infrastructure.http

import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.smp.identity.application.UpdateUserPreferencesCommand
import com.profiletailors.smp.identity.application.UserPreferencesService
import com.profiletailors.smp.identity.domain.UserPreferences
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class UserPreferencesResponse(
    val principalId: String,
    val locale: String,
    val timezone: String,
    val timeFormat: String,
    val dateFormat: String,
    val weekStartsOn: String,
    val theme: String,
) {
    companion object {
        /**
         * Creates an API response from user preferences.
         *
         * @param domain The user preferences to convert.
         * @return The corresponding user preferences response.
         */
        fun fromDomain(domain: UserPreferences): UserPreferencesResponse = UserPreferencesResponse(
            principalId = domain.principalId,
            locale = domain.locale,
            timezone = domain.timezone,
            timeFormat = domain.timeFormat,
            dateFormat = domain.dateFormat,
            weekStartsOn = domain.weekStartsOn,
            theme = domain.theme,
        )
    }
}

data class UpdateUserPreferencesRequest(
    val locale: String,
    val timezone: String,
    val timeFormat: String,
    val dateFormat: String,
    val weekStartsOn: String,
    val theme: String,
)

@Validated
@RestController
@RequestMapping("/api/auth/me/preferences")
@Tag(name = "User Preferences", description = "Current user preferences management endpoints")
class UserPreferencesController(
    private val service: UserPreferencesService,
    private val principalContextProvider: PrincipalContextProvider,
) {

    /**
     * Retrieves the preferences for the currently authenticated user.
     *
     * @return The current user's preferences.
     */
    @Operation(
        summary = "Get current user preferences",
        description = "Returns user preferences for the currently authenticated user.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @GetMapping(version = "1")
    suspend fun getPreferences(): ResponseEntity<UserPreferencesResponse> {
        val principal = principalContextProvider.require()
        val preferences = service.getPreferences(principal.principalId)
        return ResponseEntity.ok(UserPreferencesResponse.fromDomain(preferences))
    }

    /**
     * Updates the preferences of the currently authenticated user.
     *
     * @return The updated user preferences.
     */
    @Operation(
        summary = "Update current user preferences",
        description = "Updates user preferences for the currently authenticated user.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @PutMapping(version = "1")
    suspend fun updatePreferences(
        @RequestBody request: UpdateUserPreferencesRequest,
    ): ResponseEntity<UserPreferencesResponse> {
        val principal = principalContextProvider.require()
        val command = UpdateUserPreferencesCommand(
            locale = request.locale,
            timezone = request.timezone,
            timeFormat = request.timeFormat,
            dateFormat = request.dateFormat,
            weekStartsOn = request.weekStartsOn,
            theme = request.theme,
        )
        val updated = service.updatePreferences(principal.principalId, command)
        return ResponseEntity.ok(UserPreferencesResponse.fromDomain(updated))
    }
}
