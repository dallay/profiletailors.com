package com.profiletailors.smp.identity.infrastructure.security

import com.profiletailors.smp.audit.domain.AuditHook
import com.profiletailors.smp.audit.domain.AuthorizationDecisionAuditFact
import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.authorization.domain.AuthorizationReasonCode
import com.profiletailors.smp.credentials.application.ApiKeyCredentialFailureReason
import com.profiletailors.smp.credentials.application.ApiKeyCredentialNotActiveException
import com.profiletailors.smp.credentials.application.FederatedTokenValidator
import com.profiletailors.smp.credentials.application.RefreshSessionProperties
import com.profiletailors.smp.credentials.application.ServiceAccountCredentialFailureReason
import com.profiletailors.smp.credentials.application.ServiceAccountCredentialNotActiveException
import com.profiletailors.smp.identity.infrastructure.ApiKeyAuthenticatedPrincipalMaterializer
import com.profiletailors.smp.identity.infrastructure.JwtAuthenticatedPrincipalMaterializer
import com.profiletailors.smp.platform.domain.RequestContextStore
import kotlinx.coroutines.reactor.mono
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.ServerAuthenticationEntryPoint
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsConfigurationSource
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

private const val WORKSPACE_HEADER_NAME = "X-Workspace-Id"

@ConfigurationProperties(prefix = "app.security.cors")
data class CorsConfigurationProperties(
    val allowedOrigins: List<String> = emptyList(),
    val allowedMethods: List<String> = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"),
    val allowedHeaders: List<String> =
        listOf("Content-Type", "Authorization", "X-Requested-With", WORKSPACE_HEADER_NAME),
    val exposedHeaders: List<String> = emptyList(),
    val allowCredentials: Boolean = false,
    val maxAge: Long = 1800,
) {
    companion object {
        val REQUIRED_CORS_HEADERS: List<String> =
            listOf("Content-Type", "Authorization", "X-Requested-With", WORKSPACE_HEADER_NAME)
    }
}

@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(CorsConfigurationProperties::class)
class IdentitySecurityConfiguration {

    /**
     * Groups all [WebFilter] beans consumed by [securityWebFilterChain] so the method
     * signature stays under SonarQube's 7-parameter limit.
     */
    data class IdentityWebFilters(
        val apiKeyAuthentication: WebFilter,
        val authenticatedPrincipalContext: WebFilter,
        val refreshSessionOriginValidation: WebFilter,
        val revokedCredentialAudit: WebFilter,
    )

    @Bean
    fun identityWebFilters(
        apiKeyAuthenticationWebFilter: WebFilter,
        refreshSessionProperties: RefreshSessionProperties,
        revokedCredentialAuditWebFilter: WebFilter,
        requestContextStore: RequestContextStore,
        corsProperties: CorsConfigurationProperties,
    ): IdentityWebFilters = IdentityWebFilters(
        apiKeyAuthentication = apiKeyAuthenticationWebFilter,
        authenticatedPrincipalContext = AuthenticatedPrincipalContextWebFilter(requestContextStore),
        refreshSessionOriginValidation = RefreshSessionOriginValidationWebFilter(
            corsProperties = corsProperties,
            refreshSessionProperties = refreshSessionProperties,
        ),
        revokedCredentialAudit = revokedCredentialAuditWebFilter,
    )

    @Bean
    @Suppress("FunctionNameMaxLength")
    fun securityResponseHeadersWebFilter(): WebFilter = SecurityResponseHeadersWebFilter()

    @Bean
    fun corsConfigurationSource(corsProperties: CorsConfigurationProperties): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = corsProperties.allowedOrigins
        configuration.allowedMethods = corsProperties.allowedMethods
        val allowedHeaders = corsProperties.allowedHeaders.toMutableList()
        allowedHeaders.addAll(CorsConfigurationProperties.REQUIRED_CORS_HEADERS)
        configuration.allowedHeaders = allowedHeaders.distinct()
        configuration.exposedHeaders = corsProperties.exposedHeaders
        configuration.allowCredentials = corsProperties.allowCredentials
        configuration.maxAge = corsProperties.maxAge
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    @Bean
    fun jwtPrincipalAuthenticationConverter(
        jwtValidatedTokenMapper: FederatedTokenValidator<Jwt>,
        jwtAuthenticatedPrincipalMaterializer: JwtAuthenticatedPrincipalMaterializer,
    ): JwtPrincipalAuthenticationConverter =
        JwtPrincipalAuthenticationConverter(jwtValidatedTokenMapper, jwtAuthenticatedPrincipalMaterializer)

    @Bean
    fun apiKeyPrincipalAuthenticationConverter(
        apiKeyCredentialStateLookup: com.profiletailors.smp.credentials.application.ApiKeyCredentialStateLookup,
        apiKeyAuthenticatedPrincipalMaterializer: ApiKeyAuthenticatedPrincipalMaterializer,
    ): ApiKeyPrincipalAuthenticationConverter =
        ApiKeyPrincipalAuthenticationConverter(apiKeyCredentialStateLookup, apiKeyAuthenticatedPrincipalMaterializer)

    @Bean
    fun apiKeyAuthenticationWebFilter(
        apiKeyPrincipalAuthenticationConverter: ApiKeyPrincipalAuthenticationConverter,
        authenticationEntryPoint: ServerAuthenticationEntryPoint,
    ): WebFilter = ApiKeyAuthenticationWebFilter(apiKeyPrincipalAuthenticationConverter, authenticationEntryPoint)

    @Bean
    fun securityWebFilterChain(
        http: ServerHttpSecurity,
        jwtPrincipalAuthenticationConverter: JwtPrincipalAuthenticationConverter,
        filters: IdentityWebFilters,
        authenticationEntryPoint: ServerAuthenticationEntryPoint,
    ): SecurityWebFilterChain = http
        // The API remains stateless. JWT bearer tokens and API keys do not need CSRF protection,
        // while refresh/logout are protected explicitly by RefreshSessionOriginValidationWebFilter
        // because they rely on the HttpOnly refresh-session cookie.
        .csrf { it.disable() }
        .cors { }
        .authorizeExchange {
            it.pathMatchers(
                HttpMethod.GET,
                "/actuator/health",
                "/actuator/health/**",
                "/actuator/prometheus",
                "/api/capabilities/public",
                "/api/media/assets/*/preview",
                "/api/media/assets/*/content",
            ).permitAll()
                .pathMatchers(
                    HttpMethod.POST,
                    "/api/auth/verify-email",
                ).permitAll()
                .pathMatchers(
                    HttpMethod.POST,
                    "/api/auth/login",
                    "/api/auth/register",
                    "/api/auth/refresh",
                    "/api/auth/logout",
                    "/api/auth/resend-verification",
                    "/api/auth/forgot-password",
                    "/api/auth/reset-password",
                ).permitAll()
                .pathMatchers(
                    HttpMethod.POST,
                    "/api/waitlists/*/entries",
                ).permitAll()
                .anyExchange().authenticated()
        }
        .exceptionHandling { exceptions ->
            exceptions.authenticationEntryPoint(authenticationEntryPoint)
        }
        .oauth2ResourceServer { oauth2 ->
            oauth2.authenticationEntryPoint(authenticationEntryPoint)
            oauth2.jwt { jwt ->
                jwt.jwtAuthenticationConverter(jwtPrincipalAuthenticationConverter)
            }
        }
        .addFilterBefore(filters.refreshSessionOriginValidation, SecurityWebFiltersOrder.AUTHENTICATION)
        .addFilterAt(filters.apiKeyAuthentication, SecurityWebFiltersOrder.AUTHENTICATION)
        .addFilterBefore(filters.revokedCredentialAudit, SecurityWebFiltersOrder.AUTHENTICATION)
        .addFilterAfter(filters.authenticatedPrincipalContext, SecurityWebFiltersOrder.AUTHENTICATION)
        .build()

    @Bean
    fun revokedCredentialAuditWebFilter(auditHook: AuditHook): WebFilter = RevokedCredentialAuditWebFilter(auditHook)

    @Bean
    fun authenticationEntryPoint(auditHook: AuditHook): ServerAuthenticationEntryPoint =
        ServerAuthenticationEntryPoint { exchange, exception ->
            val serviceAccountCredentialException = exception.findServiceAccountCredentialException()
            val apiKeyCredentialException = exception.findApiKeyCredentialException()
            val auditMono = when {
                serviceAccountCredentialException?.reason == ServiceAccountCredentialFailureReason.REVOKED &&
                    exchange.request.path.pathWithinApplication().value() == WORKSPACE_ACCESS_PATH -> {
                    mono {
                        auditHook.onAuthorizationDecision(
                            AuthorizationDecisionAuditFact(
                                requestName = WORKSPACE_ACCESS_REQUEST_NAME,
                                requestPath = WORKSPACE_ACCESS_PATH,
                                permission = WORKSPACE_ACCESS_PERMISSION,
                                principalId = serviceAccountCredentialException.principalId
                                    ?: serviceAccountCredentialException.subject,
                                workspaceId = exchange.request.headers.getFirst(WORKSPACE_HEADER_NAME),
                                decision = AuthorizationDecision.DENY.name,
                                reasonCode = AuthorizationReasonCode.REVOKED_CREDENTIAL.name,
                            ),
                        )
                    }
                }

                apiKeyCredentialException != null &&
                    apiKeyCredentialException.reason in setOf(
                        ApiKeyCredentialFailureReason.REVOKED,
                        ApiKeyCredentialFailureReason.INACTIVE,
                        ApiKeyCredentialFailureReason.REPLACED,
                    ) &&
                    exchange.request.path.pathWithinApplication().value() == WORKSPACE_ACCESS_PATH -> {
                    mono {
                        auditHook.onAuthorizationDecision(
                            AuthorizationDecisionAuditFact(
                                requestName = WORKSPACE_ACCESS_REQUEST_NAME,
                                requestPath = WORKSPACE_ACCESS_PATH,
                                permission = WORKSPACE_ACCESS_PERMISSION,
                                principalId = apiKeyCredentialException.principalId ?: "API_KEY",
                                workspaceId = exchange.request.headers.getFirst(WORKSPACE_HEADER_NAME),
                                decision = AuthorizationDecision.DENY.name,
                                reasonCode = AuthorizationReasonCode.REVOKED_CREDENTIAL.name,
                            ),
                        )
                    }
                }

                else -> Mono.empty()
            }

            exchange.response.statusCode = HttpStatus.UNAUTHORIZED
            auditMono.then(exchange.response.setComplete())
        }

    private class RevokedCredentialAuditWebFilter(private val auditHook: AuditHook) : WebFilter {
        override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> = chain.filter(exchange)
            .onErrorResume(ServiceAccountCredentialNotActiveException::class.java) { exception ->
                if (
                    exception.reason == ServiceAccountCredentialFailureReason.REVOKED &&
                    exchange.request.path.pathWithinApplication().value() == WORKSPACE_ACCESS_PATH
                ) {
                    mono {
                        auditHook.onAuthorizationDecision(
                            AuthorizationDecisionAuditFact(
                                requestName = WORKSPACE_ACCESS_REQUEST_NAME,
                                requestPath = WORKSPACE_ACCESS_PATH,
                                permission = WORKSPACE_ACCESS_PERMISSION,
                                principalId = exception.principalId ?: exception.subject,
                                workspaceId = exchange.request.headers.getFirst(WORKSPACE_HEADER_NAME),
                                decision = AuthorizationDecision.DENY.name,
                                reasonCode = AuthorizationReasonCode.REVOKED_CREDENTIAL.name,
                            ),
                        )
                    }.then(Mono.error(exception))
                } else {
                    Mono.error(exception)
                }
            }
            .onErrorResume(ApiKeyCredentialNotActiveException::class.java) { exception ->
                if (
                    exception.reason in setOf(
                        ApiKeyCredentialFailureReason.REVOKED,
                        ApiKeyCredentialFailureReason.INACTIVE,
                    ) &&
                    exchange.request.path.pathWithinApplication().value() == WORKSPACE_ACCESS_PATH
                ) {
                    mono {
                        auditHook.onAuthorizationDecision(
                            AuthorizationDecisionAuditFact(
                                requestName = WORKSPACE_ACCESS_REQUEST_NAME,
                                requestPath = WORKSPACE_ACCESS_PATH,
                                permission = WORKSPACE_ACCESS_PERMISSION,
                                principalId = exception.principalId ?: "API_KEY",
                                workspaceId = exchange.request.headers.getFirst(WORKSPACE_HEADER_NAME),
                                decision = AuthorizationDecision.DENY.name,
                                reasonCode = AuthorizationReasonCode.REVOKED_CREDENTIAL.name,
                            ),
                        )
                    }.then(Mono.error(exception))
                } else {
                    Mono.error(exception)
                }
            }
    }

    companion object {
        internal fun Throwable.findServiceAccountCredentialException(): ServiceAccountCredentialNotActiveException? {
            var current: Throwable? = this
            while (current != null) {
                if (current is ServiceAccountCredentialNotActiveException) {
                    return current
                }
                current = current.cause
            }
            return null
        }

        internal fun Throwable.findApiKeyCredentialException(): ApiKeyCredentialNotActiveException? {
            var current: Throwable? = this
            while (current != null) {
                if (current is ApiKeyCredentialNotActiveException) {
                    return current
                }
                current = current.cause
            }
            return null
        }

        internal const val WORKSPACE_ACCESS_PATH = "/api/authorization/workspace-access/current"
        internal const val WORKSPACE_ACCESS_REQUEST_NAME =
            "com.profiletailors.smp.authorization.application.current.workspace.GetCurrentWorkspaceAccessSummaryQuery"
        internal const val WORKSPACE_ACCESS_PERMISSION = "workspace:access:read"
    }
}
