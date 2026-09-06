package com.profiletailors.smp.platformadmin.infrastructure.persistence

private const val REDACTED_VALUE = "[REDACTED]"

private val SENSITIVE_SUBSTRINGS = listOf(
    "password",
    "secret",
    "token",
    "key",
    "credential",
    "auth",
    "bearer",
)

fun redact(metadata: Map<String, String>): Map<String, String> = metadata.mapValues { (k, v) ->
    if (SENSITIVE_SUBSTRINGS.any { sensitive -> k.lowercase().contains(sensitive) }) REDACTED_VALUE else v
}
