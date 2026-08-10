package com.profiletailors.smp.governance.domain

import com.profiletailors.common.domain.ValueObject

/**
 * Lifecycle state of a [TakedownReport].
 *
 * State transitions:
 * - REPORTED  → APPROVED | DISMISSED
 * - APPROVED  → (terminal)
 * - DISMISSED → (terminal)
 */
@ValueObject
enum class TakedownReportStatus {
    /** Report has been submitted and is pending review. */
    REPORTED,

    /** Report has been reviewed and the takedown request is approved. */
    APPROVED,

    /** Report has been reviewed and the takedown request is dismissed. */
    DISMISSED,

    /**
     * Content associated with the report has been temporarily suspended
     * from public view while the report is under review.
     */
    SUSPENDED,
}
