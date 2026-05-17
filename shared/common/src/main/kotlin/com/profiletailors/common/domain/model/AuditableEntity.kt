package com.profiletailors.common.domain.model

import com.profiletailors.common.domain.SYSTEM_USER
import java.io.Serializable
import java.time.Instant

abstract class AuditableEntity(
    open val createdAt: Instant = Instant.now(),
    open val createdBy: String = SYSTEM_USER,
    open var updatedAt: Instant? = null,
    open var updatedBy: String? = null
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
