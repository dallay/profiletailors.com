package com.profiletailors.smp.platformadmin.domain

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PlatformAdminExceptionsTest {

    @Test
    fun `should expose stable messages when platform admin exceptions are created`() {
        val exceptionsWithMessages = listOf(
            PlatformRoleRequiredException() to "An active platform role assignment is required.",
            WaitlistEntryNotFoundException("wl-1") to "Waitlist entry not found: wl-1",
            WaitlistEntryNotInvitableException("wl-1", "reason") to "Waitlist entry wl-1 is not invitable: reason",
            WaitlistEntryAlreadyConvertedException("wl-1") to "Waitlist entry is already converted: wl-1",
            WaitlistEntryAlreadyCancelledException("wl-1") to "Waitlist entry is already cancelled: wl-1",
            WaitlistEntryVersionConflictException("wl-1") to
                "Concurrent modification detected for waitlist entry: wl-1",
            InvitationNotFoundException("inv-1") to "Invitation not found: inv-1",
            InvitationNotResendableException("inv-1") to "Invitation cannot be resent: inv-1",
            InvitationNotRevocableException("inv-1") to "Invitation cannot be revoked: inv-1",
            InvitationNotExpirableException("inv-1") to "Invitation cannot be expired: inv-1",
            InvitationRateLimitExceededException("wl-1") to "Invitation resend rate limit exceeded for entry: wl-1",
            InvitationVersionConflictException("inv-1") to
                "Concurrent modification detected for invitation: inv-1",
            UserNotFoundException("p-1") to "User not found: p-1",
            PlatformRoleAlreadyRevokedException("as-1") to
                "Platform role assignment is already revoked: as-1",
            PlatformRoleVersionConflictException("as-1") to
                "Concurrent modification detected for platform role assignment: as-1",
        )

        exceptionsWithMessages.forEach { (exception, expectedMessage) ->
            exception.message shouldBe expectedMessage
        }
    }

    @Test
    fun `should expose permission when platform access is denied`() {
        val exception = PlatformAccessDeniedException(PlatformPermission.USERS_READ)

        exception.permission shouldBe PlatformPermission.USERS_READ
        exception.message shouldBe "Platform permission required: platform.users.read"
    }

    @Test
    fun `should retain cause when active invitation already exists`() {
        val cause = RuntimeException("root cause")

        val exception = InvitationAlreadyActiveException("wl-1", cause)

        exception.message shouldBe "An active invitation already exists for waitlist entry: wl-1"
        exception.cause shouldBe cause
    }

    @Test
    fun `should infer email mismatch when reason contains email and match ignoring case`() {
        val exception = InvitationNotAcceptableException("The EMAIL does not MATCH the invitation")

        exception.failureCode shouldBe InvitationAcceptanceFailureCode.EMAIL_MISMATCH
        exception.message shouldBe "Invitation is unavailable."
    }

    @Test
    fun `should use invalid code when reason lacks either email or match`() {
        listOf(
            "Some other reason",
            "The email is unavailable",
            "The invitation does not match",
        ).forEach { reason ->
            InvitationNotAcceptableException(reason).failureCode shouldBe InvitationAcceptanceFailureCode.INVALID
        }
    }

    @Test
    fun `should use invalid code when no failure code is provided`() {
        val exception = InvitationNotAcceptableException()

        exception.failureCode shouldBe InvitationAcceptanceFailureCode.INVALID
        exception.message shouldBe "Invitation is unavailable."
    }
}
