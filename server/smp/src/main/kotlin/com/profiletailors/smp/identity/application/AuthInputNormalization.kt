package com.profiletailors.smp.identity.application

internal fun normalizeEmail(email: String): String = email.trim().lowercase()

internal fun normalizeUsername(username: String?, normalizedEmail: String): String =
    normalizeOptionalUsername(username) ?: normalizedEmail.substringBefore('@')

internal fun normalizeOptionalUsername(username: String?): String? =
    username?.trim()?.takeIf { it.isNotEmpty() }
