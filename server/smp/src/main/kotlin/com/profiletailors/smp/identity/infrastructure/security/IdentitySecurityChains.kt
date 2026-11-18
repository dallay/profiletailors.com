package com.profiletailors.smp.identity.infrastructure.security

import com.profiletailors.smp.identity.infrastructure.security.IdentitySecurityConfiguration.IdentityWebFilters
import org.springframework.http.HttpMethod
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity.AuthorizeExchangeSpec
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.ServerAuthenticationEntryPoint
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler
import org.springframework.web.server.WebFilter

class IdentitySecurityChains(
    private val jwtPrincipalAuthenticationConverter: JwtPrincipalAuthenticationConverter,
    private val apiKeyPrincipalAuthenticationConverter: ApiKeyPrincipalAuthenticationConverter,
    private val filters: IdentityWebFilters,
    private val authenticationEntryPoint: ServerAuthenticationEntryPoint,
    private val bffLoginSuccessHandler: ServerAuthenticationSuccessHandler,
    private val adminMfaFilter: AdminMfaFilter,
) {
    fun legacyChain(http: ServerHttpSecurity): SecurityWebFilterChain = http
        .csrf { it.requireCsrfProtectionMatcher(bffSessionCsrfMatcher()) }
        .cors { }
        .authorizeExchange { it.applyLegacyAuthorization() }
        .exceptionHandling { it.authenticationEntryPoint(authenticationEntryPoint) }
        .oauth2ResourceServer { oauth2 ->
            oauth2.authenticationEntryPoint(authenticationEntryPoint)
            oauth2.jwt { it.jwtAuthenticationConverter(jwtPrincipalAuthenticationConverter) }
        }
        .oauth2Login { it.authenticationSuccessHandler(bffLoginSuccessHandler) }
        .addFilterBefore(BffLoginReturnPathCaptureWebFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
        .addFilterBefore(filters.refreshSessionOriginValidation, SecurityWebFiltersOrder.AUTHENTICATION)
        .addFilterBefore(MixedAuthenticationCredentialsWebFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
        .addFilterBefore(filters.revokedCredentialAudit, SecurityWebFiltersOrder.AUTHENTICATION)
        .addFilterBefore(apiKeyAuthenticationFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
        .addFilterAfter(filters.authenticatedPrincipalContext, SecurityWebFiltersOrder.AUTHENTICATION)
        .addFilterAfter(BffAbsoluteSessionExpiryWebFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
        .addFilterBefore(adminMfaAuthorizationFilter(), SecurityWebFiltersOrder.AUTHORIZATION)
        .build()

    fun targetHumanChain(http: ServerHttpSecurity): SecurityWebFilterChain = http
        .csrf { it.requireCsrfProtectionMatcher(bffSessionCsrfMatcher()) }
        .cors { }
        .authorizeExchange { it.applyTargetAuthorization() }
        .exceptionHandling { it.authenticationEntryPoint(authenticationEntryPoint) }
        .oauth2Login { it.authenticationSuccessHandler(bffLoginSuccessHandler) }
        .addFilterBefore(BffLoginReturnPathCaptureWebFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
        .addFilterBefore(filters.refreshSessionOriginValidation, SecurityWebFiltersOrder.AUTHENTICATION)
        .addFilterBefore(MixedAuthenticationCredentialsWebFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
        .addFilterBefore(filters.revokedCredentialAudit, SecurityWebFiltersOrder.AUTHENTICATION)
        .addFilterAfter(filters.authenticatedPrincipalContext, SecurityWebFiltersOrder.AUTHENTICATION)
        .addFilterAfter(BffAbsoluteSessionExpiryWebFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
        .addFilterBefore(adminMfaAuthorizationFilter(), SecurityWebFiltersOrder.AUTHORIZATION)
        .build()

    private fun adminMfaAuthorizationFilter(): WebFilter = WebFilter { exchange, chain ->
        adminMfaFilter.filter(exchange, chain)
    }

    private fun apiKeyAuthenticationFilter() = ApiKeyAuthenticationWebFilter(
        apiKeyPrincipalAuthenticationConverter,
        authenticationEntryPoint,
    )

    private fun AuthorizeExchangeSpec.applyLegacyAuthorization() {
        pathMatchers(HttpMethod.GET, *PUBLIC_READ_PATHS).permitAll()
            .pathMatchers(HttpMethod.POST, "/api/auth/verify-email").permitAll()
            .pathMatchers(HttpMethod.POST, *LEGACY_PUBLIC_WRITE_PATHS).permitAll()
            .pathMatchers(HttpMethod.POST, "/api/waitlists/*/entries").permitAll()
            .anyExchange().authenticated()
    }

    private fun AuthorizeExchangeSpec.applyTargetAuthorization() {
        pathMatchers(HttpMethod.GET, *PUBLIC_READ_PATHS).permitAll()
            .pathMatchers(HttpMethod.POST, "/api/auth/logout", "/api/bootstrap/complete").permitAll()
            .pathMatchers(HttpMethod.POST, "/api/waitlists/*/entries").permitAll()
            .anyExchange().authenticated()
    }

    private companion object {
        val PUBLIC_READ_PATHS = arrayOf(
            "/api/auth/csrf",
            "/actuator/health",
            "/actuator/health/**",
            "/api/capabilities/public",
            "/api/media/assets/*/preview",
            "/api/media/assets/*/content",
            "/api/media/proxy",
            "/api/bootstrap/requirements",
            "/{shortCode:[0-9A-Za-z]{1,32}}",
        )

        val LEGACY_PUBLIC_WRITE_PATHS = arrayOf(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/auth/resend-verification",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/bootstrap/complete",
        )
    }
}
