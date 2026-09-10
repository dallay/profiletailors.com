package com.profiletailors.smp.platformadmin.infrastructure.http

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.profiletailors.smp.platformadmin.domain.InvitationAcceptanceFailureCode
import com.profiletailors.smp.platformadmin.domain.InvitationAlreadyActiveException
import com.profiletailors.smp.platformadmin.domain.InvitationNotAcceptableException
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange

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
    fun `sanitizes invalid correlation ids before logging`() {
        val logger = LoggerFactory.getLogger(AdminProblemDetailsHandler::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)

        try {
            val maliciousHeader = "abc\r\nX-Evil: injected"
            val oversizedHeader = "A".repeat(200)
            val exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/users")
                    .header("X-Correlation-Id", maliciousHeader)
                    .header("X-Request-Id", oversizedHeader)
                    .build(),
            )

            handler.handle(PlatformAccessDeniedException(PlatformPermission.USERS_READ), exchange)

            val message = appender.list.single().formattedMessage
            assertTrue(message.contains("correlationId=unknown"))
            assertFalse(message.contains(maliciousHeader))
            assertFalse(message.contains(oversizedHeader))
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }

    @Test
    fun `logs a valid correlation id without sanitizing it`() {
        val logger = LoggerFactory.getLogger(AdminProblemDetailsHandler::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)

        try {
            val exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/users")
                    .header("X-Correlation-Id", "trace-123")
                    .build(),
            )

            handler.handle(PlatformAccessDeniedException(PlatformPermission.USERS_READ), exchange)

            val message = appender.list.single().formattedMessage
            assertTrue(message.contains("correlationId=trace-123"))
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }

    @Test
    fun `falls back to unknown when no safe correlation id is present`() {
        val logger = LoggerFactory.getLogger(AdminProblemDetailsHandler::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)

        try {
            val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/admin/users").build())

            handler.handle(PlatformAccessDeniedException(PlatformPermission.USERS_READ), exchange)

            val message = appender.list.single().formattedMessage
            assertTrue(message.contains("correlationId=unknown"))
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
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
    fun `maps invitation email mismatch to a stable redacted problem detail`() {
        val problem = handler.handle(
            InvitationNotAcceptableException(InvitationAcceptanceFailureCode.EMAIL_MISMATCH),
        )

        assertEquals(HttpStatus.FORBIDDEN.value(), problem.status)
        assertEquals("Invitation unavailable", problem.title)
        assertEquals("Invitation is unavailable.", problem.detail)
        assertEquals("INVITATION_EMAIL_MISMATCH", problem.properties?.get("code"))
        assertFalse(problem.detail?.contains("EMAIL_MISMATCH") == true)
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
