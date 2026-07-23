package com.profiletailors.smp.authorization.infrastructure.http

import com.profiletailors.smp.authorization.domain.AuthorizationDeniedException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class AuthorizationProblemDetailsHandlerTest {

    private val handler = AuthorizationProblemDetailsHandler()

    @Test
    fun `authorization denied returns generic forbidden problem detail`() {
        val problem = handler.handle(AuthorizationDeniedException("Permission workspace.manage was explicitly denied."))

        assertEquals(HttpStatus.FORBIDDEN.value(), problem.status)
        assertEquals("Authorization denied", problem.title)
        assertEquals("You do not have permission to perform this action.", problem.detail)
    }
}