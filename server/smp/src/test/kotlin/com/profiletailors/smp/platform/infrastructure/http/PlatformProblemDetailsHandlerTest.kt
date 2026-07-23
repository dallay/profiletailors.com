package com.profiletailors.smp.platform.infrastructure.http

import com.profiletailors.common.domain.context.MissingPrincipalContextException
import com.profiletailors.common.domain.context.MissingResourceContextException
import com.profiletailors.smp.credentials.application.RefreshSessionFailureReason
import com.profiletailors.smp.credentials.application.RefreshSessionNotActiveException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class PlatformProblemDetailsHandlerTest {

    private val handler = PlatformProblemDetailsHandler()

    @Test
    fun `maps missing principal context to unauthorized problem detail`() {
        val problem = handler.handle(MissingPrincipalContextException())

        assertEquals(HttpStatus.UNAUTHORIZED.value(), problem.status)
        assertEquals("Principal context missing", problem.title)
        assertEquals("Authentication is required.", problem.detail)
    }

    @Test
    fun `maps missing resource context to bad request problem detail`() {
        val problem = handler.handle(MissingResourceContextException())

        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.status)
        assertEquals("Resource context missing", problem.title)
        assertEquals("The request is missing required context.", problem.detail)
    }

    @Test
    fun `maps invalid refresh session to generic unauthorized problem detail`() {
        val problem = handler.handle(
            RefreshSessionNotActiveException(
                lookupKey = "lookup-1",
                reason = RefreshSessionFailureReason.INVALID,
            ),
        )

        assertEquals(HttpStatus.UNAUTHORIZED.value(), problem.status)
        assertEquals("Refresh session invalid", problem.title)
        assertEquals("Session is not active.", problem.detail)
    }
}
