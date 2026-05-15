package com.profiletailors.smp.identity.infrastructure.security

import com.profiletailors.smp.credentials.application.FederatedTokenValidator
import com.profiletailors.smp.credentials.infrastructure.security.SpringJwtValidatedTokenMapper
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.infrastructure.JwtAuthenticatedPrincipalMaterializer
import com.profiletailors.smp.platform.infrastructure.RequestContextStore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.web.server.WebFilter

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
    fun authenticatedPrincipalContextWebFilter(requestContextStore: RequestContextStore): WebFilter =
        AuthenticatedPrincipalContextWebFilter(requestContextStore)

    @Bean
    fun securityWebFilterChain(
        http: ServerHttpSecurity,
        jwtPrincipalAuthenticationConverter: JwtPrincipalAuthenticationConverter,
        authenticatedPrincipalContextWebFilter: WebFilter,
        workspaceContextWebFilter: WebFilter,
    ): SecurityWebFilterChain =
        http
            .csrf { it.disable() }
            .authorizeExchange {
                it.pathMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                    .anyExchange().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(jwtPrincipalAuthenticationConverter)
                }
            }
            .addFilterAfter(authenticatedPrincipalContextWebFilter, org.springframework.security.config.web.server.SecurityWebFiltersOrder.AUTHENTICATION)
            .addFilterAfter(workspaceContextWebFilter, org.springframework.security.config.web.server.SecurityWebFiltersOrder.AUTHENTICATION)
            .build()
}
