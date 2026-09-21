package com.profiletailors.smp.platformadmin.application.query

import java.time.Instant

data class NotificationFilters(
    val status: String? = null,
    val channel: String? = null,
    val templateId: String? = null,
    val recipient: String? = null,
    val createdFrom: Instant? = null,
    val createdTo: Instant? = null,
)
