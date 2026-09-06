package com.profiletailors.smp.credentials.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CredentialValueObjectsTest {
    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `session cookie rejects a blank name`(name: String) {
        shouldThrow<IllegalArgumentException> {
            sessionCookie(name = name)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `session cookie rejects a blank path`(path: String) {
        shouldThrow<IllegalArgumentException> {
            sessionCookie(path = path)
        }
    }

    @Test
    fun `session cookie accepts nonblank name and path`() {
        val cookie = sessionCookie()

        cookie.name shouldBe "refresh_token"
        cookie.path shouldBe "/api/auth"
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `validated token rejects a blank token value`(tokenValue: String) {
        shouldThrow<IllegalArgumentException> {
            validatedToken(tokenValue = tokenValue)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `validated token rejects a blank subject`(subject: String) {
        shouldThrow<IllegalArgumentException> {
            validatedToken(subject = subject)
        }
    }

    @Test
    fun `validated token accepts nonblank token value and subject`() {
        val token = validatedToken()

        token.tokenValue shouldBe "signed-token"
        token.subject shouldBe "principal-1"
    }

    private fun sessionCookie(name: String = "refresh_token", path: String = "/api/auth") = SessionCookie(
        name = name,
        value = "cookie-value",
        path = path,
        sameSite = "Strict",
        secure = true,
        httpOnly = true,
        maxAgeSeconds = 3600,
    )

    private fun validatedToken(tokenValue: String = "signed-token", subject: String = "principal-1") = ValidatedToken(
        credentialType = CredentialType.JWT,
        tokenValue = tokenValue,
        subject = subject,
        issuer = "profile-tailors",
        audience = setOf("profile-tailors-api"),
        issuedAt = null,
        expiresAt = null,
    )
}
