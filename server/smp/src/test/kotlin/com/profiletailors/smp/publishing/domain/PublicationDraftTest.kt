package com.profiletailors.smp.publishing.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PublicationDraftTest {

    @Test
    fun `queueableStatus returns QUEUED for NOW mode`() {
        val draft = createDraft(ScheduleMode.NOW)
        assertEquals(PublicationStatus.QUEUED, draft.queueableStatus())
    }

    @Test
    fun `queueableStatus returns SCHEDULED for SCHEDULED_AT mode`() {
        val draft = createDraft(ScheduleMode.SCHEDULED_AT)
        assertEquals(PublicationStatus.SCHEDULED, draft.queueableStatus())
    }

    @Test
    fun `queueableStatus returns SCHEDULED for NEXT_SLOT mode`() {
        val draft = createDraft(ScheduleMode.NEXT_SLOT)
        assertEquals(PublicationStatus.SCHEDULED, draft.queueableStatus())
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
    )
}

class GrantedScopeBundleTest {
    @Test
    fun `fromGrantedScopes includes supported bundles`() {
        val scopes = setOf("w_member_social")
        val bundle = GrantedScopeBundle.fromGrantedScopes(scopes)

        assertTrue(bundle.capabilityBundles.contains(LinkedinCapabilityBundle.PERSONAL_PROFILE_TEXT))
        assertTrue(bundle.capabilityBundles.contains(LinkedinCapabilityBundle.PERSONAL_PROFILE_IMAGE))
        // Should not include unsupported or ones missing scopes
        assertFalse(bundle.capabilityBundles.contains(LinkedinCapabilityBundle.ANALYTICS))
        assertFalse(bundle.capabilityBundles.contains(LinkedinCapabilityBundle.ORG_PAGE_TEXT))
    }
}
