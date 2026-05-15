package com.profiletailors.smp.platform.infrastructure.http

import com.profiletailors.smp.tenancy.application.MissingActiveWorkspaceException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class TenancyProblemDetailsHandlerTest {

    private val handler = PlatformProblemDetailsHandler()

    @Test
    fun `maps missing active workspace to bad request problem detail`() {
        val problem = handler.handle(MissingActiveWorkspaceException())

        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.status)
        assertEquals("Active workspace missing", problem.title)
        assertEquals("Active workspace identifier is required.", problem.detail)
    }
}
