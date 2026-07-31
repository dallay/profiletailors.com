package com.profiletailors.smp.platformadmin.application.model

import java.time.Instant

data class AdminWaitlistEntrySummary(
    val id: String,
    val waitlistId: String,
    val waitlistKey: String,
    val email: String,
    val normalizedEmail: String,
    val status: String,
    val joinedAt: Instant,
    val invitedAt: Instant?,
    val convertedAt: Instant?,
    val cancelledAt: Instant?,
    val preferredLocale: String?,
    val source: String,
)

data class AdminWaitlistEntryDetail(
    val id: String,
    val waitlistId: String,
    val waitlistKey: String,
    val email: String,
    val normalizedEmail: String,
    val status: String,
    val joinedAt: Instant,
    val invitedAt: Instant?,
    val convertedAt: Instant?,
    val cancelledAt: Instant?,
    val preferredLocale: String?,
    val earlyAccessConsent: Boolean,
    val marketingConsent: Boolean,
    val consentVersion: String?,
    val source: String,
    val metadataSummary: Map<String, String>,
    val invitationHistory: List<AdminInvitationSummary>,
    val version: Long,
)
