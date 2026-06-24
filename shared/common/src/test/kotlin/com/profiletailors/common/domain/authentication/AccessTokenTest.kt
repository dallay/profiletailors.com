package com.profiletailors.common.domain.authentication

import com.profiletailors.common.domain.bus.query.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class AccessTokenTest {

    @Test
    fun `should create access token with all parameters`() {
        val token = AccessToken(
            token = "jwt-token",
            expiresIn = 3600L,
            refreshToken = "refresh-token",
            refreshExpiresIn = 7200L,
            tokenType = "Bearer",
            notBeforePolicy = 0,
            sessionState = "session-id",
            scope = "openid profile",
        )

        assertThat(token.token).isEqualTo("jwt-token")
        assertThat(token.expiresIn).isEqualTo(3600L)
        assertThat(token.refreshToken).isEqualTo("refresh-token")
        assertThat(token.refreshExpiresIn).isEqualTo(7200L)
        assertThat(token.tokenType).isEqualTo("Bearer")
        assertThat(token.notBeforePolicy).isEqualTo(0)
        assertThat(token.sessionState).isEqualTo("session-id")
        assertThat(token.scope).isEqualTo("openid profile")
    }

    @Test
    fun `should default nullable fields to null`() {
        val token = AccessToken(
            token = "jwt",
            expiresIn = 300L,
            refreshToken = "rt",
            refreshExpiresIn = 600L,
            tokenType = "Bearer",
        )

        assertThat(token.notBeforePolicy).isNull()
        assertThat(token.sessionState).isNull()
        assertThat(token.scope).isNull()
    }

    @Test
    fun `should implement Response marker interface`() {
        val token = AccessToken(
            token = "jwt",
            expiresIn = 300L,
            refreshToken = "rt",
            refreshExpiresIn = 600L,
            tokenType = "Bearer",
        )

        assertThat(token).isInstanceOf(Response::class.java)
    }

    @Test
    fun `should support data class equality`() {
        val token1 = AccessToken("jwt", 300L, "rt", 600L, "Bearer")
        val token2 = AccessToken("jwt", 300L, "rt", 600L, "Bearer")

        assertThat(token1).isEqualTo(token2)
        assertThat(token1.hashCode()).isEqualTo(token2.hashCode())
    }

    @Test
    fun `should distinguish tokens with different values`() {
        val token1 = AccessToken("jwt-a", 300L, "rt-a", 600L, "Bearer")
        val token2 = AccessToken("jwt-b", 300L, "rt-b", 600L, "Bearer")

        assertThat(token1).isNotEqualTo(token2)
    }
}
