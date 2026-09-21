package com.profiletailors.smp.platformadmin.application.model

import java.time.Instant

data class NotificationSummary(
    val id: String,
    val channel: String,
    val templateId: String,
    val recipient: String,
    val status: String,
    val errorMessage: String?,
    val createdAt: Instant,
    val sentAt: Instant?,
    val failedAt: Instant?,
    val redactedPayload: Map<String, Any?>,
)
