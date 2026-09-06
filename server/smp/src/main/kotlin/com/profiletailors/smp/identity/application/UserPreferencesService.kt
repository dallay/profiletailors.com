package com.profiletailors.smp.identity.application

import com.profiletailors.smp.identity.domain.UserPreferences
import org.springframework.stereotype.Service

data class UpdateUserPreferencesCommand(
    val locale: String,
    val timezone: String,
    val timeFormat: String,
    val dateFormat: String,
    val weekStartsOn: String,
    val theme: String,
)

@Service
class UserPreferencesService(private val userPreferencesGateway: UserPreferencesGateway) {
    suspend fun getPreferences(principalId: String): UserPreferences =
        userPreferencesGateway.findByPrincipalId(principalId)
            ?: UserPreferences(principalId = principalId)

    suspend fun updatePreferences(principalId: String, command: UpdateUserPreferencesCommand): UserPreferences {
        val existing = getPreferences(principalId)
        val updated = existing.copy(
            locale = command.locale,
            timezone = command.timezone,
            timeFormat = command.timeFormat,
            dateFormat = command.dateFormat,
            weekStartsOn = command.weekStartsOn,
            theme = command.theme,
        )
        return userPreferencesGateway.save(updated)
    }
}
