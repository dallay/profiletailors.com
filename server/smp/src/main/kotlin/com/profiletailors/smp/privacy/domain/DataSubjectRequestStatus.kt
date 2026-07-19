package com.profiletailors.smp.privacy.domain

/**
 * Lifecycle status of a [DataSubjectRequest].
 *
 * State machine for Phase 1: PENDING → COMPLETED | REJECTED | FAILED
 * Terminal states (COMPLETED, REJECTED, FAILED) reject any further transitions.
 *
 * @since 1.0.0
 */
enum class DataSubjectRequestStatus {
    PENDING,
    COMPLETED,
    REJECTED,
    FAILED;

    /**
     * Returns `true` when a transition from this status to [target] is valid.
     *
     * Only [PENDING] may transition to [COMPLETED], [REJECTED], or [FAILED].
     * All terminal states reject every target.
     */
    fun canTransitionTo(target: DataSubjectRequestStatus): Boolean {
        if (this == PENDING) {
            return target != PENDING
        }
        return false
    }

    /**
     * Validates and performs a state transition.
     *
     * @throws IllegalStateException if the transition is not allowed.
     */
    fun transitionTo(target: DataSubjectRequestStatus): Boolean {
        check(canTransitionTo(target)) {
            "Cannot transition from $this to $target"
        }
        return true
    }
}
