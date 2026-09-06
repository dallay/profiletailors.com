package com.profiletailors.smp.identity.application

import com.profiletailors.smp.identity.domain.UserPreferences

interface UserPreferencesGateway {
    suspend fun findByPrincipalId(principalId: String): UserPreferences?
    suspend fun save(preferences: UserPreferences): UserPreferences
}
