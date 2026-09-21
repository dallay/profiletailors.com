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

    private val ELIGIBLE_TEMPLATES = setOf(
        "platform.password-recovery",
        "platform.password-reset",
    )

    /**
     * Returns true if the given template ID is eligible for admin-initiated retry.
     *
     * Matching is exact: only the two reviewed password-recovery templates are eligible.
     * Invitation and waitlist templates are excluded because they have their own resend
     * mechanisms at the domain level and must not be retried via the admin path.
     * Unknown templates are denied by default.
     */
    fun isEligible(templateId: TemplateId): Boolean = templateId.value in ELIGIBLE_TEMPLATES
}
