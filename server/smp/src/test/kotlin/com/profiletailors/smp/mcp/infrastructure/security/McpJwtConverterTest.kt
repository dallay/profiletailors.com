package com.profiletailors.smp.mcp.infrastructure.security

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

/**
 * TDD test for McpJwtConverter.
 *
 * Drives the implementation of JWT converter that validates audience
 * and extracts workspace_id claim into McpAuthenticationToken.
 */
@Tag("fast")
class McpJwtConverterTest {

    private val converter = McpJwtConverter()

    @Test
    fun `convert JWT with correct audience and workspace_id returns McpAuthenticationToken`() {
        val jwt = Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .claim("aud", "https://api.profiletailors.com/api/mcp")
            .claim("workspace_id", "ws-123")
            .claim("sub", "user-456")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()

        val result = converter.convert(jwt)

        assertThat(result).isNotNull
        assertThat(result).isInstanceOf(McpAuthenticationToken::class.java)
        val token = result as McpAuthenticationToken
        assertThat(token.workspaceId).isEqualTo("ws-123")
        assertThat(token.name).isEqualTo("user-456")
        assertThat(token.isAuthenticated).isTrue()
    }

    @Test
    fun `convert JWT without audience claim throws BadCredentialsException`() {
        val jwt = Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .claim("workspace_id", "ws-123")
            .claim("sub", "user-456")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()

        assertThatThrownBy { converter.convert(jwt) }
            .isInstanceOf(BadCredentialsException::class.java)
    }

    @Test
    fun `convert JWT with wrong audience throws BadCredentialsException`() {
        val jwt = Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .claim("aud", "https://wrong-audience.com")
            .claim("workspace_id", "ws-123")
            .claim("sub", "user-456")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()

        assertThatThrownBy { converter.convert(jwt) }
            .isInstanceOf(BadCredentialsException::class.java)
    }

    @Test
    fun `convert JWT without workspace_id claim throws BadCredentialsException`() {
        val jwt = Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .claim("aud", "https://api.profiletailors.com/api/mcp")
            .claim("sub", "user-456")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()

        assertThatThrownBy { converter.convert(jwt) }
            .isInstanceOf(BadCredentialsException::class.java)
    }

    @Test
    fun `convert JWT with audience as list containing correct value returns McpAuthenticationToken`() {
        val jwt = Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .claim("aud", listOf("https://api.profiletailors.com/api/mcp", "other-audience"))
            .claim("workspace_id", "ws-789")
            .claim("sub", "user-999")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()

        val result = converter.convert(jwt)

        assertThat(result).isNotNull
        val token = result as McpAuthenticationToken
        assertThat(token.workspaceId).isEqualTo("ws-789")
    }
}
