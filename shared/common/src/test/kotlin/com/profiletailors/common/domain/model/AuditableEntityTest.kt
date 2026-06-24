package com.profiletailors.common.domain.model

import com.profiletailors.common.domain.SYSTEM_USER
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

internal class AuditableEntityTest {

    @Test
    fun `should set createdBy to SYSTEM_USER by default`() {
        val entity = TestAuditableEntity()

        assertThat(entity.createdBy).isEqualTo(SYSTEM_USER)
    }

    @Test
    fun `should set createdAt to current time`() {
        val before = Instant.now()
        val entity = TestAuditableEntity()
        val after = Instant.now()

        assertThat(entity.createdAt).isBetween(before, after)
    }

    @Test
    fun `should initialize updated fields as null`() {
        val entity = TestAuditableEntity()

        assertThat(entity.updatedAt).isNull()
        assertThat(entity.updatedBy).isNull()
    }

    @Test
    fun `should allow overriding createdBy`() {
        val entity = TestAuditableEntity(createdBy = "user-42")

        assertThat(entity.createdBy).isEqualTo("user-42")
    }

    @Test
    fun `should allow setting updated fields after creation`() {
        val entity = TestAuditableEntity()
        val now = Instant.now()

        entity.updatedAt = now
        entity.updatedBy = "user-42"

        assertThat(entity.updatedAt).isEqualTo(now)
        assertThat(entity.updatedBy).isEqualTo("user-42")
    }

    private open class TestAuditableEntity(
        override val createdAt: Instant = Instant.now(),
        override val createdBy: String = SYSTEM_USER,
        override var updatedAt: Instant? = null,
        override var updatedBy: String? = null,
    ) : AuditableEntity(createdAt, createdBy, updatedAt, updatedBy)
}
