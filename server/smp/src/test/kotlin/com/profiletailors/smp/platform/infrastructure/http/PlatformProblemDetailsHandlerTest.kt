package com.profiletailors.smp.platform.infrastructure.http

import com.profiletailors.common.domain.context.MissingPrincipalContextException
import com.profiletailors.common.domain.context.MissingResourceContextException
import com.profiletailors.smp.credentials.application.RefreshSessionFailureReason
import com.profiletailors.smp.credentials.application.RefreshSessionNotActiveException
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class PlatformProblemDetailsHandlerTest {

    private val handler = PlatformProblemDetailsHandler()

    @Test
    fun `maps missing principal context to unauthorized problem detail`() {
        val problem = handler.handle(MissingPrincipalContextException())

        problem.status shouldBe HttpStatus.UNAUTHORIZED.value()
        problem.title shouldBe "Principal context missing"
        problem.detail shouldBe "Authentication is required."
    }

    @Test
    fun `maps missing resource context to bad request problem detail`() {
        val problem = handler.handle(MissingResourceContextException())

        problem.status shouldBe HttpStatus.BAD_REQUEST.value()
        problem.title shouldBe "Resource context missing"
        problem.detail shouldBe "The request is missing required context."
    }

    @Test
    fun `maps invalid refresh session to generic unauthorized problem detail`() {
        val problem = handler.handle(
            RefreshSessionNotActiveException(
                lookupKey = "lookup-1",
                reason = RefreshSessionFailureReason.INVALID,
            ),
        )

        problem.status shouldBe HttpStatus.UNAUTHORIZED.value()
        problem.title shouldBe "Refresh session invalid"
        problem.detail shouldBe "Session is not active."
        problem.properties?.get("lookupKey").shouldBeNull()
    }
}
