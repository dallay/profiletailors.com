package com.profiletailors.smp.identity.application

import com.profiletailors.smp.identity.domain.UserPreferences

interface UserPreferencesGateway {
    /**
 * Finds user preferences for a principal.
 *
 * @return The user's preferences, or `null` if no preferences are found.
 */
suspend fun findByPrincipalId(principalId: String): UserPreferences?
    /**
 * Persists user preferences.
 *
 * @param preferences The user preferences to persist.
 * @return The persisted user preferences.
 */
suspend fun save(preferences: UserPreferences): UserPreferences
}
