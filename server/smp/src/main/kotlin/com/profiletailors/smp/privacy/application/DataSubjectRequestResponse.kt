package com.profiletailors.smp.privacy.application

import java.time.Instant

/**
 * Response DTO for all data subject request operations.
 */
data class DataSubjectRequestResponse(
    val id: String,
    val type: String,
    val status: String,
    val requestedBy: String,
    val requestedByEmail: String,
    val workspaceId: String?,
    val resultRef: String?,
    val rejectionReason: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant?,
)
