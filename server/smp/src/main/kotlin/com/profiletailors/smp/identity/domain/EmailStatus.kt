package com.profiletailors.smp.identity.domain

/**
 * Email verification status for a user identity.
 *
 * Represents the lifecycle state of email verification:
 * - [PENDING] — user has registered and verification email has been dispatched
 * - [VERIFIED] — user has successfully verified ownership of their email
 * - [BOUNCED] — verification email bounced (invalid address, full mailbox, etc.)
 */
enum class EmailStatus {
    PENDING,
    VERIFIED,
    BOUNCED,
}
