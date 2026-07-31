package com.profiletailors.smp.platformadmin.infrastructure.http

import com.profiletailors.smp.platformadmin.domain.InvitationAlreadyActiveException
import com.profiletailors.smp.platformadmin.domain.InvitationNotResendableException
import com.profiletailors.smp.platformadmin.domain.InvitationNotRevocableException
import com.profiletailors.smp.platformadmin.domain.InvitationRateLimitExceededException
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.UserNotFoundException
import com.profiletailors.smp.platformadmin.domain.WaitlistEntryAlreadyCancelledException
import com.profiletailors.smp.platformadmin.domain.WaitlistEntryAlreadyConvertedException
import com.profiletailors.smp.platformadmin.domain.WaitlistEntryNotFoundException
import com.profiletailors.smp.platformadmin.domain.WaitlistEntryNotInvitableException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class AdminProblemDetailsHandlerTest {

    private val handler = AdminProblemDetailsHandler()

    @Test
    fun `maps PlatformAccessDeniedException to 403 with platform code`() {
        val problem = handler.handle(PlatformAccessDeniedException(PlatformPermission.USERS_READ))

        assertEquals(HttpStatus.FORBIDDEN.value(), problem.status)
        assertEquals("PLATFORM_ACCESS_DENIED", problem.properties?.get("code"))
        assertEquals("urn:profiletailors:error:PLATFORM_ACCESS_DENIED", problem.type.toString())
    }

    @Test
    fun `maps WaitlistEntryNotFoundException to 404 with entry code`() {
        val problem = handler.handle(WaitlistEntryNotFoundException("entry-1"))

        assertEquals(HttpStatus.NOT_FOUND.value(), problem.status)
        assertEquals("WAITLIST_ENTRY_NOT_FOUND", problem.properties?.get("code"))
    }

    @Test
    fun `maps WaitlistEntryNotInvitableException to 409 with not invitable code`() {
        val problem = handler.handle(WaitlistEntryNotInvitableException("entry-1", "not eligible"))

        assertEquals(HttpStatus.CONFLICT.value(), problem.status)
        assertEquals("WAITLIST_ENTRY_NOT_INVITABLE", problem.properties?.get("code"))
    }

    @Test
    fun `maps WaitlistEntryAlreadyConvertedException to 409 with already converted code`() {
        val problem = handler.handle(WaitlistEntryAlreadyConvertedException("entry-1"))

        assertEquals(HttpStatus.CONFLICT.value(), problem.status)
        assertEquals("WAITLIST_ENTRY_ALREADY_CONVERTED", problem.properties?.get("code"))
    }

    @Test
    fun `maps WaitlistEntryAlreadyCancelledException to 409 with already cancelled code`() {
        val problem = handler.handle(WaitlistEntryAlreadyCancelledException("entry-1"))

        assertEquals(HttpStatus.CONFLICT.value(), problem.status)
        assertEquals("WAITLIST_ENTRY_ALREADY_CANCELLED", problem.properties?.get("code"))
    }

    @Test
    fun `maps InvitationAlreadyActiveException to 409 with already active code`() {
        val problem = handler.handle(InvitationAlreadyActiveException("entry-1"))

        assertEquals(HttpStatus.CONFLICT.value(), problem.status)
        assertEquals("INVITATION_ALREADY_ACTIVE", problem.properties?.get("code"))
    }

    @Test
    fun `maps InvitationNotResendableException to 409 with not resendable code`() {
        val problem = handler.handle(InvitationNotResendableException("inv-1"))

        assertEquals(HttpStatus.CONFLICT.value(), problem.status)
        assertEquals("INVITATION_NOT_RESENDABLE", problem.properties?.get("code"))
    }

    @Test
    fun `maps InvitationNotRevocableException to 409 with not revocable code`() {
        val problem = handler.handle(InvitationNotRevocableException("inv-1"))

        assertEquals(HttpStatus.CONFLICT.value(), problem.status)
        assertEquals("INVITATION_NOT_REVOCABLE", problem.properties?.get("code"))
    }

    @Test
    fun `maps InvitationRateLimitExceededException to 429 with rate limit code`() {
        val problem = handler.handle(InvitationRateLimitExceededException("entry-1"))

        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), problem.status)
        assertEquals("INVITATION_RATE_LIMIT_EXCEEDED", problem.properties?.get("code"))
    }

    @Test
    fun `maps UserNotFoundException to 404 with user code`() {
        val problem = handler.handle(UserNotFoundException("user-1"))

        assertEquals(HttpStatus.NOT_FOUND.value(), problem.status)
        assertEquals("USER_NOT_FOUND", problem.properties?.get("code"))
    }

    @Test
    fun `maps IllegalArgumentException to 400 with validation code`() {
        val problem = handler.handle(IllegalArgumentException("invalid argument"))

        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.status)
        assertEquals("VALIDATION_ERROR", problem.properties?.get("code"))
        assertEquals("urn:profiletailors:error:VALIDATION_ERROR", problem.type.toString())
    }
}
