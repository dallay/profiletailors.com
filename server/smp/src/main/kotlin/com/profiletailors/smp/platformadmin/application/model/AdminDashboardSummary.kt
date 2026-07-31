package com.profiletailors.smp.platformadmin.application.model

data class AdminDashboardSummary(
    val pendingCount: Long,
    val invitedCount: Long,
    val convertedCount: Long,
    val cancelledCount: Long,
    val activeInvitationCount: Long,
    val invitationsExpiringIn24h: Long,
    val invitationsExpiringIn7d: Long,
    val failedDeliveryCount: Long,
    val registrationsInPeriod: Long,
    val periodDays: Int,
)
