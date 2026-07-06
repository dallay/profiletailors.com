package com.profiletailors.smp.publishing.domain

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class PublicationDraftTest {

    @Test
    fun `queueableStatus returns QUEUED for NOW mode`() {
        val draft = createDraft(ScheduleMode.NOW)
        draft.queueableStatus() shouldBe PublicationStatus.QUEUED
    }

    @Test
    fun `queueableStatus returns SCHEDULED for SCHEDULED_AT mode`() {
        val draft = createDraft(ScheduleMode.SCHEDULED_AT)
        draft.queueableStatus() shouldBe PublicationStatus.SCHEDULED
    }

    @Test
    fun `queueableStatus returns SCHEDULED for NEXT_SLOT mode`() {
        val draft = createDraft(ScheduleMode.NEXT_SLOT)
        draft.queueableStatus() shouldBe PublicationStatus.SCHEDULED
    }

    private fun createDraft(mode: ScheduleMode) = PublicationDraft(
        id = "1",
        workspaceId = "ws-1",
        authorPrincipalId = "u-1",
        provider = SocialProvider.LINKEDIN,
        socialAccountId = "acc-1",
        status = PublicationStatus.DRAFT,
        scheduleMode = mode,
        priority = false,
        createdAt = Instant.now(),
    )
}

class GrantedScopeBundleTest {
    @Test
    fun `fromGrantedScopes includes supported bundles`() {
        val scopes = setOf("w_member_social")
        val bundle = GrantedScopeBundle.fromGrantedScopes(scopes)

        bundle.capabilityBundles.contains(LinkedinCapabilityBundle.PERSONAL_PROFILE_TEXT) shouldBe true
        bundle.capabilityBundles.contains(LinkedinCapabilityBundle.PERSONAL_PROFILE_IMAGE) shouldBe true
        bundle.capabilityBundles.contains(LinkedinCapabilityBundle.ANALYTICS) shouldBe false
        bundle.capabilityBundles.contains(LinkedinCapabilityBundle.ORG_PAGE_TEXT) shouldBe false
    }
}
