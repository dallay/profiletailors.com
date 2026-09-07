package com.profiletailors.observability

data class OperationalEvent(
    val name: String,
    val severity: Severity,
    val message: String? = null,
    val attributes: Map<String, Any?> = emptyMap(),
    val cause: Throwable? = null,
)
