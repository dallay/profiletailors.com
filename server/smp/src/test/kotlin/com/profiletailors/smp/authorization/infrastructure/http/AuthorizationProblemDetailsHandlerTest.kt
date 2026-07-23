package com.profiletailors.smp.authorization.infrastructure.http

import com.profiletailors.smp.authorization.domain.AuthorizationDeniedException
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class AuthorizationProblemDetailsHandlerTest {

    private val handler = AuthorizationProblemDetailsHandler()

    @Test
    fun `authorization denied returns generic forbidden problem detail`() {
        val problem = handler.handle(AuthorizationDeniedException("Permission workspace.manage was explicitly denied."))

        problem.status shouldBe HttpStatus.FORBIDDEN.value()
        problem.title shouldBe "Authorization denied"
        problem.detail shouldBe "You do not have permission to perform this action."
    }
}