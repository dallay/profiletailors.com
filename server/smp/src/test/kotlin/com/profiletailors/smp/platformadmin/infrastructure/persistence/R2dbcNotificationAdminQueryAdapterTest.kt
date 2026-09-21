package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.profiletailors.smp.platformadmin.application.model.NotificationSummary
import com.profiletailors.smp.platformadmin.application.model.PagedResult
import com.profiletailors.smp.platformadmin.application.query.NotificationFilters
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class R2dbcNotificationAdminQueryAdapterTest {

    @Test
    fun `NotificationFilters captures all filter parameters`() {
        val filters = NotificationFilters(
            status = "FAILED",
            channel = "EMAIL",
            templateId = "platform.password-recovery",
            recipient = "test@example.com",
            createdFrom = Instant.now().minusSeconds(86_400),
            createdTo = Instant.now(),
        )

        assertThat(filters.status).isEqualTo("FAILED")
        assertThat(filters.channel).isEqualTo("EMAIL")
        assertThat(filters.templateId).isEqualTo("platform.password-recovery")
        assertThat(filters.recipient).isEqualTo("test@example.com")
        assertThat(filters.createdFrom).isNotNull()
        assertThat(filters.createdTo).isNotNull()
    }

    @Test
    fun `NotificationFilters with no filters returns null values`() {
        val filters = NotificationFilters()

        assertThat(filters.status).isNull()
        assertThat(filters.channel).isNull()
        assertThat(filters.templateId).isNull()
        assertThat(filters.recipient).isNull()
        assertThat(filters.createdFrom).isNull()
        assertThat(filters.createdTo).isNull()
    }

    @Test
    fun `PagedResult computes pagination correctly`() {
        val items = listOf(
            NotificationSummary(
                id = "id-1",
                channel = "EMAIL",
                templateId = "platform.password-recovery",
                recipient = "test@example.com",
                status = "FAILED",
                errorMessage = "Error",
                createdAt = Instant.now(),
                sentAt = null,
                failedAt = Instant.now(),
                redactedPayload = emptyMap(),
            ),
        )
        val pagedResult = PagedResult.of(items, page = 0, size = 20, totalElements = 1)

        assertThat(pagedResult.items).hasSize(1)
        assertThat(pagedResult.page).isEqualTo(0)
        assertThat(pagedResult.size).isEqualTo(20)
        assertThat(pagedResult.totalElements).isEqualTo(1)
        assertThat(pagedResult.totalPages).isEqualTo(1)
        assertThat(pagedResult.hasNext).isFalse()
        assertThat(pagedResult.hasPrevious).isFalse()
    }

    @Test
    fun `PagedResult computes hasNext and hasPrevious correctly`() {
        val items = listOf<NotificationSummary>()
        val pagedResult = PagedResult.of(items, page = 1, size = 10, totalElements = 25)

        assertThat(pagedResult.hasNext).isTrue()
        assertThat(pagedResult.hasPrevious).isTrue()
    }
}
