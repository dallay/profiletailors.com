package com.profiletailors.smp.publishing.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant

class ChangedPublishingModelsTest {
    private val now = Instant.parse("2026-09-06T00:00:00Z")

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `notification event rejects a blank workspace id`(workspaceId: String) {
        shouldThrow<IllegalArgumentException> {
            notificationEvent(workspaceId)
        }
    }

    @Test
    fun `provider catalog item rejects a negative connected channel count`() {
        shouldThrow<IllegalArgumentException> {
            providerCatalogItem(connectedChannelCount = -1)
        }
    }

    @Test
    fun `provider catalog item accepts no connected channels`() {
        providerCatalogItem(connectedChannelCount = 0).connectedChannelCount shouldBe 0
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `recurring schedule rejects a blank id`(id: String) {
        shouldThrow<IllegalArgumentException> {
            recurringSchedule(id = id)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `recurring schedule rejects a blank workspace id`(workspaceId: String) {
        shouldThrow<IllegalArgumentException> {
            recurringSchedule(workspaceId = workspaceId)
        }
    }

    @Test
    fun `recurring schedule accepts nonblank identifiers`() {
        val schedule = recurringSchedule()

        schedule.id shouldBe "schedule-1"
        schedule.workspaceId shouldBe "workspace-1"
    }

    private fun notificationEvent(workspaceId: String) = NotificationEvent(
        id = "notification-1",
        workspaceId = workspaceId,
        provider = SocialProvider.LINKEDIN,
        socialAccountId = "account-1",
        category = NotificationCategory.PUBLICATION_SUCCEEDED,
        message = "Published",
        occurredAt = now,
    )

    private fun providerCatalogItem(connectedChannelCount: Int) = ProviderCatalogItem(
        provider = SocialProvider.LINKEDIN,
        accountKinds = setOf(SocialAccountKind.PERSONAL_PROFILE.name),
        state = ProviderCatalogState.AVAILABLE,
        reason = null,
        channelLimit = null,
        connectedChannelCount = connectedChannelCount,
        canConnectMore = true,
    )

    private fun recurringSchedule(id: String = "schedule-1", workspaceId: String = "workspace-1") = RecurringSchedule(
        id = id,
        workspaceId = workspaceId,
        createdBy = "principal-1",
        templatePostId = "post-1",
        recurrenceRule = RecurrenceRule(RecurrenceFrequency.DAILY),
        timezone = "UTC",
        nextScheduledAt = now,
        status = RecurringScheduleStatus.ACTIVE,
    )
}
