package com.profiletailors.common.domain.model

import com.profiletailors.common.domain.SYSTEM_USER
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

internal class AuditableEntityTest {

    private fun defaultEntity(): AuditableEntity = object : AuditableEntity() {}

    private open class AuditableEntityWithOverrides(
        createdAt: Instant = Instant.now(),
        createdBy: String = SYSTEM_USER,
        updatedAt: Instant? = null,
        updatedBy: String? = null,
    ) : AuditableEntity(createdAt, createdBy, updatedAt, updatedBy)

    @Test
    fun `should set createdBy to SYSTEM_USER by default`() {
        val entity = defaultEntity()

        assertThat(entity.createdBy).isEqualTo(SYSTEM_USER)
    }

    @Test
    fun `should set createdAt to current time`() {
        val before = Instant.now()
        val entity = defaultEntity()
        val after = Instant.now()

        assertThat(entity.createdAt).isBetween(before, after)
    }

    @Test
    fun `should initialize updated fields as null`() {
        val entity = defaultEntity()

        assertThat(entity.updatedAt).isNull()
        assertThat(entity.updatedBy).isNull()
    }

    @Test
    fun `should allow overriding createdBy`() {
        val entity = AuditableEntityWithOverrides(createdBy = "user-42")

        assertThat(entity.createdBy).isEqualTo("user-42")
    }

    @Test
    fun `should allow setting updated fields after creation`() {
        val entity = AuditableEntityWithOverrides()
        val now = Instant.now()

        entity.updatedAt = now
        entity.updatedBy = "user-42"

        assertThat(entity.updatedAt).isEqualTo(now)
        assertThat(entity.updatedBy).isEqualTo("user-42")
    }
}
