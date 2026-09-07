package com.profiletailors.smp.identity.domain

import java.time.Instant

data class UserPreferences(
    val principalId: String,
    val locale: String = "en",
    val timezone: String = "UTC",
    val timeFormat: String = "24h",
    val dateFormat: String = "DD/MM/YYYY",
    val weekStartsOn: String = "Monday",
    val theme: String = "dark",
    val updatedAt: Instant = Instant.now(),
)
