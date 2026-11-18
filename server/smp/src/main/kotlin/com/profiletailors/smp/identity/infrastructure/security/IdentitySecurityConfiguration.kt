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
import com.profiletailors.smp.identity.application.ExternalIdentityResolutionService
import com.profiletailors.smp.identity.infrastructure.ApiKeyAuthenticatedPrincipalMaterializer
import com.profiletailors.smp.identity.infrastructure.JwtAuthenticatedPrincipalMaterializer
import com.profiletailors.smp.identity.infrastructure.OidcAuthenticatedPrincipalResolver
import kotlinx.coroutines.reactor.mono
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.userinfo.ReactiveOAuth2UserService
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.ServerAuthenticationEntryPoint
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsConfigurationSource
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import org.springframework.web.server.session.CookieWebSessionIdResolver
import org.springframework.web.server.session.DefaultWebSessionManager
import org.springframework.web.server.session.InMemoryWebSessionStore
import org.springframework.web.server.session.WebSessionIdResolver
import org.springframework.web.server.session.WebSessionManager
import reactor.core.publisher.Mono
import java.time.Duration

private const val WORKSPACE_HEADER_NAME = "X-Workspace-Id"
private const val BFF_CSRF_HEADER_NAME = "X-CSRF-TOKEN"
private const val BFF_SESSION_IDLE_MINUTES = 30L

internal fun bffSessionCsrfMatcher() = ServerWebExchangeMatcher { exchange ->
    val unsafeMethod = exchange.request.method !in setOf(
        HttpMethod.GET,
        HttpMethod.HEAD,
        HttpMethod.OPTIONS,
        HttpMethod.TRACE,
    )
    val hasBffSession = exchange.request.cookies.containsKey(IdentitySecurityConfiguration.BFF_SESSION_COOKIE_NAME)
    if (unsafeMethod && hasBffSession) {
        ServerWebExchangeMatcher.MatchResult.match()
    } else {
        ServerWebExchangeMatcher.MatchResult.notMatch()
    }
}

@Configuration
class BffSessionConfiguration {
    @Bean
    fun webSessionIdResolver(): WebSessionIdResolver = CookieWebSessionIdResolver().apply {
        setCookieName(IdentitySecurityConfiguration.BFF_SESSION_COOKIE_NAME)
        addCookieInitializer { cookie ->
            cookie.path("/")
                .secure(true)
                .httpOnly(true)
                .sameSite("Lax")
        }
    }

    @Bean
    fun webSessionManager(sessionIdResolver: WebSessionIdResolver): WebSessionManager =
        DefaultWebSessionManager().apply {
            setSessionIdResolver(sessionIdResolver)
            setSessionStore(
                InMemoryWebSessionStore().apply {
                    defaultMaxIdleTime = Duration.ofMinutes(BFF_SESSION_IDLE_MINUTES)
                },
            )
        }

    @Bean
    fun bffLoginSuccessHandler(
        @Value("\${app.email.public-app-url}") publicAppUrl: String,
        @Value("\${app.security.cors.admin-public-origin:}") adminPublicOrigin: String,
    ): ServerAuthenticationSuccessHandler = BffLoginSuccessHandler(publicAppUrl, adminPublicOrigin)
}

@ConfigurationProperties(prefix = "app.security.cors")
data class CorsConfigurationProperties(
    val allowedOrigins: List<String> = emptyList(),
    val adminPublicOrigin: String = "",
    val allowedMethods: List<String> = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"),
    val allowedHeaders: List<String> =
        listOf("Content-Type", "Authorization", "X-Requested-With", WORKSPACE_HEADER_NAME),
    val exposedHeaders: List<String> = emptyList(),
    val allowCredentials: Boolean = false,
    val maxAge: Long = 1800,
) {
    val effectiveAllowedOrigins: List<String> =
        (allowedOrigins + listOfNotNull(adminPublicOrigin.takeIf { it.isNotBlank() })).distinct()

    companion object {
        val REQUIRED_CORS_HEADERS: List<String> =
            listOf("Content-Type", "Authorization", "X-Requested-With", WORKSPACE_HEADER_NAME, BFF_CSRF_HEADER_NAME)
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
        val authenticatedPrincipalContext: WebFilter,
        val refreshSessionOriginValidation: WebFilter,
        val revokedCredentialAudit: WebFilter,
    )

    @Bean
    fun identityWebFilters(
        refreshSessionProperties: RefreshSessionProperties,
        revokedCredentialAuditWebFilter: WebFilter,
        corsProperties: CorsConfigurationProperties,
    ): IdentityWebFilters = IdentityWebFilters(
        authenticatedPrincipalContext = AuthenticatedPrincipalContextWebFilter(),
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
        configuration.allowedOriginPatterns = corsProperties.effectiveAllowedOrigins
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
    fun oidcPrincipalResolver(
        externalIdentityResolutionService: ExternalIdentityResolutionService,
        @Value("\${spring.security.oauth2.client.provider.keycloak.issuer-uri}") issuer: String,
    ): OidcAuthenticatedPrincipalResolver = OidcAuthenticatedPrincipalResolver(
        externalIdentityResolutionService,
        issuer,
    )

    @Bean
    fun localPrincipalOidcUserService(
        oidcAuthenticatedPrincipalResolver: OidcAuthenticatedPrincipalResolver,
    ): ReactiveOAuth2UserService<OidcUserRequest, OidcUser> =
        LocalPrincipalOidcUserService(oidcAuthenticatedPrincipalResolver)

    @Bean
    fun identitySecurityChains(
        jwtPrincipalAuthenticationConverter: JwtPrincipalAuthenticationConverter,
        apiKeyPrincipalAuthenticationConverter: ApiKeyPrincipalAuthenticationConverter,
        filters: IdentityWebFilters,
        authenticationEntryPoint: ServerAuthenticationEntryPoint,
        bffLoginSuccessHandler: ServerAuthenticationSuccessHandler,
        adminMfaFilter: AdminMfaFilter,
    ): IdentitySecurityChains = IdentitySecurityChains(
        jwtPrincipalAuthenticationConverter,
        apiKeyPrincipalAuthenticationConverter,
        filters,
        authenticationEntryPoint,
        bffLoginSuccessHandler,
        adminMfaFilter,
    )

    @Bean
    fun securityWebFilterChain(
        http: ServerHttpSecurity,
        identitySecurityChains: IdentitySecurityChains,
    ): SecurityWebFilterChain = identitySecurityChains.legacyChain(http)

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
        internal const val BFF_SESSION_COOKIE_NAME = "SESSION"
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

@Configuration
class AdminAndFreshMfaFilterConfiguration {
    @Bean
    fun adminMfaFilter(
        @Value("\${spring.security.oauth2.client.provider.keycloak.issuer-uri}") issuer: String,
    ): AdminMfaFilter = AdminMfaFilter(issuer)
}

class AdminMfaFilter(expectedIssuer: String) {
    private val delegate = AdminAndFreshMfaAuthorizationWebFilter(expectedIssuer)
    val webFilter: WebFilter = delegate

    fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> = delegate.filter(exchange, chain)
}
