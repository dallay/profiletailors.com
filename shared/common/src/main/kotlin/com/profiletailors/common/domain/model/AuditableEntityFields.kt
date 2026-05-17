package com.profiletailors.common.domain.model

import java.time.Instant

interface AuditableEntityFields {
    val createdBy: String
    val createdAt: Instant
    var updatedBy: String?
    var updatedAt: Instant?
    fun isNewEntity(): Boolean = updatedAt == null || createdAt == updatedAt
}
