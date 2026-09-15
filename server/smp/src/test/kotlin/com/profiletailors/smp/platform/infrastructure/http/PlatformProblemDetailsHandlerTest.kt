package com.profiletailors.smp.platform.infrastructure.http

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class PlatformProblemDetailsHandlerTest {

    private val handler = PlatformProblemDetailsHandler()

    @Test
    fun `maps missing principal context to unauthorized problem detail`() {
        val problem = handler.handleMissingPrincipalContext()

        problem.status shouldBe HttpStatus.UNAUTHORIZED.value()
        problem.title shouldBe "Principal context missing"
        problem.detail shouldBe "Authentication is required."
    }

    @Test
    fun `maps inactive api key credential to unauthorized problem detail`() {
        val problem = handler.handleApiKeyNotActive()

        problem.status shouldBe HttpStatus.UNAUTHORIZED.value()
        problem.title shouldBe "API key credential invalid"
        problem.detail shouldBe "Authentication is required."
    }

    @Test
    fun `maps missing resource context to bad request problem detail`() {
        val problem = handler.handleMissingResourceContext()

        problem.status shouldBe HttpStatus.BAD_REQUEST.value()
        problem.title shouldBe "Resource context missing"
        problem.detail shouldBe "The request is missing required context."
    }

    @Test
    fun `maps invalid refresh session to generic unauthorized problem detail`() {
        val problem = handler.handleRefreshSessionNotActive()

        problem.status shouldBe HttpStatus.UNAUTHORIZED.value()
        problem.title shouldBe "Refresh session invalid"
        problem.detail shouldBe "Session is not active."
        problem.properties?.get("lookupKey").shouldBeNull()
    }
}
