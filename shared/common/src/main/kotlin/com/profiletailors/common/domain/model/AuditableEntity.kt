package com.profiletailors.common.domain.model

import com.profiletailors.common.domain.SYSTEM_USER
import java.io.Serializable
import java.time.Instant

/**
 * Base class for entities that track creation and modification audit fields.
 *
 * Provides four audit fields out of the box:
 * - [createdAt] / [createdBy]: set once at creation time and never modified.
 * - [updatedAt] / [updatedBy]: updated each time the entity is modified; initially null.
 *
 * The [createdBy] defaults to [SYSTEM_USER] and should be overridden via constructor
 * with the authenticated principal's identifier.
 *
 * @since 1.0.0
 * @see BaseEntity
 */
abstract class AuditableEntity(
    open val createdAt: Instant = Instant.now(),
    open val createdBy: String = SYSTEM_USER,
    open var updatedAt: Instant? = null,
    open var updatedBy: String? = null,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
