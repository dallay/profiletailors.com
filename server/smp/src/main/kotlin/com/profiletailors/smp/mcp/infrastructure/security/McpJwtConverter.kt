package com.profiletailors.smp.mcp.infrastructure.security

import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Converts JWT to McpAuthenticationToken.
 *
 * Validates that the JWT has the correct audience claim
 * (`https://api.profiletailors.com/api/mcp`) and extracts the
 * workspace_id claim.
 *
 * Throws [BadCredentialsException] if validation fails (missing/wrong audience
 * or missing workspace_id) so Spring Security returns 401.
 */
class McpJwtConverter : Converter<Jwt, AbstractAuthenticationToken> {

    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        // Validate audience
        val audience = jwt.audience
        if (audience == null || !audience.contains(EXPECTED_AUDIENCE)) {
            throw BadCredentialsException("JWT audience does not contain $EXPECTED_AUDIENCE")
        }

        // Extract workspace_id
        val workspaceId = jwt.getClaimAsString(WORKSPACE_ID_CLAIM)
            ?: throw BadCredentialsException("JWT is missing required claim: $WORKSPACE_ID_CLAIM")

        // Extract subject (user ID)
        val subject = jwt.subject
            ?: throw BadCredentialsException("JWT is missing subject claim")

        return McpAuthenticationToken(
            workspaceId = workspaceId,
            principal = subject,
        )
    }

    companion object {
        private const val EXPECTED_AUDIENCE = "https://api.profiletailors.com/api/mcp"
        private const val WORKSPACE_ID_CLAIM = "workspace_id"
    }
}
