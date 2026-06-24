package com.profiletailors.common.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

internal class AuditableEntityFieldsTest {

    @Test
    fun `should be new when updatedAt is null`() {
        val entity = TestAuditableEntity(updatedAt = null)

        assertThat(entity.isNewEntity()).isTrue()
    }

    @Test
    fun `should be new when createdAt equals updatedAt`() {
        val now = Instant.now()
        val entity = TestAuditableEntity(createdAt = now, updatedAt = now)

        assertThat(entity.isNewEntity()).isTrue()
    }

    @Test
    fun `should not be new when updatedAt differs from createdAt`() {
        val entity = TestAuditableEntity(
            createdAt = Instant.parse("2024-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2024-06-01T00:00:00Z"),
        )

        assertThat(entity.isNewEntity()).isFalse()
    }

    private open class TestAuditableEntity(
        override val createdAt: Instant = Instant.now(),
        override var updatedAt: Instant? = null,
    ) : AuditableEntityFields {
        override val createdBy: String = "system"
        override var updatedBy: String? = null
    }
}
