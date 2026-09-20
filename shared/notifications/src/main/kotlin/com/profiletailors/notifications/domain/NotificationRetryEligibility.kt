package com.profiletailors.notifications.domain

import com.profiletailors.common.domain.ValueObject

/**
 * Determines whether a notification template is eligible for admin-initiated retry.
 *
 * Only templates representing password recovery flows are eligible. Invitation templates
 * are excluded because they have their own resend mechanisms at the domain level and
 * must not be retried via the admin path.
 */
@ValueObject
object NotificationRetryEligibility {

    private val ELIGIBLE_TEMPLATE_PREFIXES = setOf(
        "platform.password-recovery",
        "platform.password-reset",
    )

    private val INELIGIBLE_TEMPLATE_PREFIXES = setOf(
        "platform.invitation",
        "platform.workspace-invitation",
        "platform.waitlist-invitation",
    )

    /**
     * Returns true if the given template ID is eligible for admin-initiated retry.
     *
     * Matching is prefix-based to accommodate template variants:
     * - `platform.password-recovery` and `platform.password-reset` are eligible
     * - `platform.invitation` and its variants are ineligible
     * - Unknown templates are denied by default
     */
    fun isEligible(templateId: TemplateId): Boolean {
        val value = templateId.value
        return when {
            INELIGIBLE_TEMPLATE_PREFIXES.any { value.startsWith(it) } -> false
            ELIGIBLE_TEMPLATE_PREFIXES.any { value.startsWith(it) } -> true
            else -> false
        }
    }
}
