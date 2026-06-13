package com.profiletailors.common.domain.model

import java.time.Instant

/**
 * Interface for entities that expose audit fields.
 *
 * Provides a default [isNewEntity] heuristic: an entity is considered "new"
 * if it has never been updated ([updatedAt] is null) or if the creation and
 * update timestamps are identical (indicating it was just persisted).
 *
 * @since 1.0.0
 */
interface AuditableEntityFields {
    val createdBy: String
    val createdAt: Instant
    var updatedBy: String?
    var updatedAt: Instant?

    /** Returns `true` if the entity has likely not been modified since creation. */
    fun isNewEntity(): Boolean = updatedAt == null || createdAt == updatedAt
}
