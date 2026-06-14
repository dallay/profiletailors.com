package com.profiletailors.smp.identity.domain

/**
 * Email verification status for a user identity.
 *
 * Represents the lifecycle state of email verification:
 * - [UNVERIFIED] — user has registered but not yet verified their email
 * - [VERIFIED] — user has successfully verified ownership of their email
 */
enum class EmailStatus {
    UNVERIFIED,
    VERIFIED,
}
