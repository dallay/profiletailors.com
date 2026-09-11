package com.profiletailors.smp.platformadmin.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class PlatformAdminExceptionsTest {

    @Test
    fun `exercises PlatformAdmin exception constructors and messages`() {
        val permEx = PlatformAccessDeniedException(PlatformPermission.USERS_READ)
        assertEquals(PlatformPermission.USERS_READ, permEx.permission)
        assertEquals("Platform permission required: users:read", permEx.message)

        val roleEx = PlatformRoleRequiredException()
        assertEquals("An active platform role assignment is required.", roleEx.message)

        val wlNotFound = WaitlistEntryNotFoundException("wl-1")
        assertEquals("Waitlist entry not found: wl-1", wlNotFound.message)

        val wlNotInvitable = WaitlistEntryNotInvitableException("wl-1", "reason")
        assertEquals("Waitlist entry wl-1 is not invitable: reason", wlNotInvitable.message)

        val wlConverted = WaitlistEntryAlreadyConvertedException("wl-1")
        assertEquals("Waitlist entry is already converted: wl-1", wlConverted.message)

        val wlCancelled = WaitlistEntryAlreadyCancelledException("wl-1")
        assertEquals("Waitlist entry is already cancelled: wl-1", wlCancelled.message)

        val wlConflict = WaitlistEntryVersionConflictException("wl-1")
        assertEquals("Concurrent modification detected for waitlist entry: wl-1", wlConflict.message)

        val invNotFound = InvitationNotFoundException("inv-1")
        assertEquals("Invitation not found: inv-1", invNotFound.message)

        val cause = RuntimeException("root cause")
        val invActive = InvitationAlreadyActiveException("wl-1", cause)
        assertEquals("An active invitation already exists for waitlist entry: wl-1", invActive.message)
        assertEquals(cause, invActive.cause)

        val invResendable = InvitationNotResendableException("inv-1")
        assertEquals("Invitation cannot be resent: inv-1", invResendable.message)

        val invRevocable = InvitationNotRevocableException("inv-1")
        assertEquals("Invitation cannot be revoked: inv-1", invRevocable.message)

        val invExpirable = InvitationNotExpirableException("inv-1")
        assertEquals("Invitation cannot be expired: inv-1", invExpirable.message)

        val rateLimit = InvitationRateLimitExceededException("wl-1")
        assertEquals("Invitation resend rate limit exceeded for entry: wl-1", rateLimit.message)

        val invVersion = InvitationVersionConflictException("inv-1")
        assertEquals("Concurrent modification detected for invitation: inv-1", invVersion.message)

        val userNotFound = UserNotFoundException("p-1")
        assertEquals("User not found: p-1", userNotFound.message)

        val roleRevoked = PlatformRoleAlreadyRevokedException("as-1")
        assertEquals("Platform role assignment is already revoked: as-1", roleRevoked.message)

        val roleVersion = PlatformRoleVersionConflictException("as-1")
        assertEquals("Concurrent modification detected for platform role assignment: as-1", roleVersion.message)
    }

    @Test
    fun `exercises InvitationNotAcceptableException string constructor email mismatch logic`() {
        val ex1 = InvitationNotAcceptableException("The email does not match the invitation")
        assertEquals(InvitationAcceptanceFailureCode.EMAIL_MISMATCH, ex1.failureCode)

        val ex2 = InvitationNotAcceptableException("Some other reason")
        assertEquals(InvitationAcceptanceFailureCode.INVALID, ex2.failureCode)
    }
}
