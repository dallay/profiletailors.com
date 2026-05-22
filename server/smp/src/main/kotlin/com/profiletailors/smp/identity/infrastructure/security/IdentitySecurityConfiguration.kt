package com.profiletailors.smp.identity.infrastructure.security

import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.credentials.application.ApiKeyCredentialFailureReason
import com.profiletailors.smp.credentials.application.ApiKeyCredentialNotActiveException
import com.profiletailors.smp.credentials.application.FederatedTokenValidator
import com.profiletailors.smp.credentials.application.ServiceAccountCredentialFailureReason
import com.profiletailors.smp.credentials.application.ServiceAccountCredentialNotActiveException
import com.profiletailors.smp.identity.infrastructure.ApiKeyAuthenticatedPrincipalMaterializer
import com.profiletailors.smp.identity.infrastructure.JwtAuthenticatedPrincipalMaterializer
import com.profiletailors.smp.audit.domain.AuditHook
import com.profiletailors.smp.audit.domain.AuthorizationDecisionAuditFact
import com.profiletailors.smp.authorization.domain.AuthorizationReasonCode
import com.profiletailors.smp.platform.infrastructure.RequestContextStore
import kotlinx.coroutines.reactor.mono
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.ServerAuthenticationEntryPoint
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@Configuration
@EnableWebFluxSecurity
class IdentitySecurityConfiguration {

    @Bean
    fun jwtValidatedTokenMapper(): FederatedTokenValidator<Jwt> = SpringJwtValidatedTokenMapper()

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
    fun authenticatedPrincipalContextWebFilter(requestContextStore: RequestContextStore): WebFilter =
        AuthenticatedPrincipalContextWebFilter(requestContextStore)

    @Bean
    fun securityWebFilterChain(
        http: ServerHttpSecurity,
        jwtPrincipalAuthenticationConverter: JwtPrincipalAuthenticationConverter,
        apiKeyAuthenticationWebFilter: WebFilter,
        authenticatedPrincipalContextWebFilter: WebFilter,
        requestPathWebFilter: WebFilter,
        workspaceContextWebFilter: WebFilter,
        revokedCredentialAuditWebFilter: WebFilter,
        authenticationEntryPoint: ServerAuthenticationEntryPoint,
    ): SecurityWebFilterChain =
        http
            .csrf { it.disable() }
            .authorizeExchange {
                it.pathMatchers(HttpMethod.GET, "/actuator/health").permitAll()
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
            .addFilterAt(apiKeyAuthenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .addFilterBefore(revokedCredentialAuditWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .addFilterAfter(authenticatedPrincipalContextWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .addFilterAfter(requestPathWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .addFilterAfter(workspaceContextWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .build()

    @Bean
    fun revokedCredentialAuditWebFilter(auditHook: AuditHook): WebFilter =
        RevokedCredentialAuditWebFilter(auditHook)

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

private class RevokedCredentialAuditWebFilter(
    private val auditHook: AuditHook,
) : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> =
        chain.filter(exchange)
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
        internal const val WORKSPACE_HEADER_NAME = "X-Workspace-Id"
    }
}
