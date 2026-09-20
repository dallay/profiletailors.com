package com.profiletailors.notifications.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class NotificationRetryEligibilityTest {

    @Test
    fun `isEligible returns true for password recovery templates`() {
        val eligibleTemplates = listOf(
            TemplateId("platform.password-recovery"),
            TemplateId("platform.password-recovery-v2"),
            TemplateId("platform.password-reset"),
            TemplateId("platform.password-reset-token"),
        )
        eligibleTemplates.forEach { templateId ->
            assertTrue(
                NotificationRetryEligibility.isEligible(templateId),
                "Expected $templateId to be eligible for retry",
            )
        }
    }

    @Test
    fun `isEligible returns false for invitation templates`() {
        val ineligibleTemplates = listOf(
            TemplateId("platform.invitation"),
            TemplateId("platform.workspace-invitation"),
            TemplateId("platform.waitlist-invitation"),
            TemplateId("platform.invitation-reminder"),
        )
        ineligibleTemplates.forEach { templateId ->
            assertFalse(
                NotificationRetryEligibility.isEligible(templateId),
                "Expected $templateId to be ineligible for retry",
            )
        }
    }

    @Test
    fun `isEligible returns false for unknown templates`() {
        val unknownTemplates = listOf(
            TemplateId("platform.welcome"),
            TemplateId("platform.announcement"),
            TemplateId("marketing.promo"),
            TemplateId("unknown.template"),
        )
        unknownTemplates.forEach { templateId ->
            assertFalse(
                NotificationRetryEligibility.isEligible(templateId),
                "Expected $templateId to be ineligible for retry",
            )
        }
    }
}
