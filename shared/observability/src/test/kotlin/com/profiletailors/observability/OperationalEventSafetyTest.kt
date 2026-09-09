package com.profiletailors.observability

import java.util.concurrent.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class OperationalEventSafetyTest {
    @Test
    fun `sanitizer removes sensitive keys case insensitively and preserves safe scalars`() {
        val event = OperationalEvent(
            name = "identity.reset.failed",
            severity = Severity.ERROR,
            message = "reset failed for workspace ws-1",
            attributes = mapOf(
                "Authorization" to "Bearer secret-value",
                "user.emailAddress" to "user@example.com",
                "workspaceId" to "ws-1",
                "attempt" to 2,
                "nullable" to null,
            ),
        )

        val sanitized = OperationalEventSanitizer.sanitize(event)

        assertEquals("reset failed for workspace ws-1", event.message)
        assertEquals("reset failed for workspace ws-1", sanitized.message)
        assertEquals(mapOf("workspaceId" to "ws-1", "attempt" to 2, "nullable" to null), sanitized.attributes)
        assertEquals("Bearer secret-value", event.attributes["Authorization"])
        assertNull(sanitized.cause)
    }

    @Test
    fun `sanitizer removes nested variants of every sensitive key family`() {
        val event = OperationalEvent(
            name = "security.event",
            severity = Severity.WARN,
            attributes = mapOf(
                "request.TOKEN.value" to "token",
                "user.PasswordHash" to "password",
                "provider.secretValue" to "secret",
                "http.authorizationHeader" to "authorization",
                "request.authContext" to "auth",
                "request.cookieHeader" to "cookie",
                "response.set-cookie" to "set-cookie",
                "provider.credential" to "credential",
                "verification.otp" to "otp",
                "profile.emailAddress" to "email",
                "subject.pii" to "pii",
                "workspaceId" to "ws-1",
            ),
        )

        val sanitized = OperationalEventSanitizer.sanitize(event)

        assertEquals(mapOf("workspaceId" to "ws-1"), sanitized.attributes)
    }

    @Test
    fun `sanitizer redacts sensitive values embedded in the message`() {
        val sanitized = OperationalEventSanitizer.sanitize(
            OperationalEvent(
                name = "identity.reset.failed",
                severity = Severity.ERROR,
                message = "reset failed token=abc123",
                attributes = mapOf("accessToken" to "abc123"),
            ),
        )

        assertEquals("reset failed token=[REDACTED]", sanitized.message)
        assertEquals(emptyMap(), sanitized.attributes)
    }

    @Test
    fun `sanitizer replaces throwable details with a safe error type`() {
        val cause = IllegalStateException("password=do-not-log")

        val sanitized = OperationalEventSanitizer.sanitize(
            OperationalEvent("operation.failed", Severity.ERROR, cause = cause),
        )

        assertEquals("IllegalStateException", sanitized.attributes["errorType"])
        assertNull(sanitized.cause)
    }

    @Test
    fun `best effort sink swallows adapter failures`() {
        val sink = BestEffortOperationalEventSink(OperationalEventSink { error("adapter down") })

        sink.emit(OperationalEvent("operation.failed", Severity.ERROR))
    }

    @Test
    fun `best effort sink preserves cancellation`() {
        val cancellation = CancellationException("cancelled")
        val sink = BestEffortOperationalEventSink(OperationalEventSink { throw cancellation })

        assertSame(
            cancellation,
            assertFailsWith<CancellationException> {
                sink.emit(OperationalEvent("operation.cancelled", Severity.INFO))
            },
        )
    }

    @Test
    fun `best effort sink does not swallow fatal errors`() {
        val fatal = AssertionError("fatal")
        val sink = BestEffortOperationalEventSink(OperationalEventSink { throw fatal })

        assertSame(
            fatal,
            assertFailsWith<AssertionError> {
                sink.emit(OperationalEvent("operation.failed", Severity.ERROR))
            },
        )
    }
}
