package com.profiletailors.smp.mcp.infrastructure.security

import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority

/**
 * Authentication token for MCP API requests.
 *
 * Carries the workspace_id claim extracted from the JWT.
 */
class McpAuthenticationToken(
    val workspaceId: String,
    private val principal: String,
    authorities: Collection<GrantedAuthority> = emptyList(),
) : AbstractAuthenticationToken(authorities) {

    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any? = null

    override fun getPrincipal(): Any = principal

    override fun getName(): String = principal
}
