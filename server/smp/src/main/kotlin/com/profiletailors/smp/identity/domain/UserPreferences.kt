package com.profiletailors.smp.identity.domain

import com.profiletailors.common.domain.ValueObject
import java.time.Instant

@ValueObject
data class UserPreferences(
    val principalId: String,
    val locale: String = "en",
    val timezone: String = "UTC",
    val timeFormat: String = "24h",
    val dateFormat: String = "DD/MM/YYYY",
    val weekStartsOn: String = "Monday",
    val theme: String = "dark",
    val updatedAt: Instant = Instant.now(),
) {
    init {
        require(principalId.isNotBlank()) { "Principal ID must not be blank" }
    }
}
