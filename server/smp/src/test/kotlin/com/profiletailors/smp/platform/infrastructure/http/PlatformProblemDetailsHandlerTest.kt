package com.profiletailors.smp.platform.infrastructure.http

import com.profiletailors.smp.platform.application.MissingPrincipalContextException
import com.profiletailors.smp.platform.application.MissingResourceContextException
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
        assertEquals("Authenticated principal context is required.", problem.detail)
    }

    @Test
    fun `maps missing resource context to bad request problem detail`() {
        val problem = handler.handle(MissingResourceContextException())

        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.status)
        assertEquals("Resource context missing", problem.title)
        assertEquals("Resolved resource context is required.", problem.detail)
    }
}
