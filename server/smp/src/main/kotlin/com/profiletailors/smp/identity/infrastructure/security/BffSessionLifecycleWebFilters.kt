package com.profiletailors.smp.identity.infrastructure.security

import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.web.server.WebFilterExchange
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant

class BffAbsoluteSessionExpiryWebFilter(
    private val clock: Clock = Clock.systemUTC(),
    private val absoluteLifetime: Duration = Duration.ofHours(BFF_SESSION_ABSOLUTE_HOURS),
) : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> =
        ReactiveSecurityContextHolder.getContext().hasElement().flatMap { hasSecurityContext ->
            if (!hasSecurityContext) {
                chain.filter(exchange)
            } else {
                ReactiveSecurityContextHolder.getContext().flatMap { securityContext ->
                    if (securityContext.authentication?.isBffAuthentication() != true) {
                        chain.filter(exchange)
                    } else {
                        exchange.session.flatMap { session ->
                            enforceAbsoluteLifetime(exchange, chain, session.attributes)
                        }
                    }
                }
            }
        }

    private fun enforceAbsoluteLifetime(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
        attributes: MutableMap<String, Any>,
    ): Mono<Void> {
        val authenticatedAt = attributes[AUTHENTICATED_AT_ATTRIBUTE]
        if (authenticatedAt == null) {
            attributes[AUTHENTICATED_AT_ATTRIBUTE] = clock.instant()
            return chain.filter(exchange)
        }

        val establishedAt = authenticatedAt as? Instant
        if (establishedAt != null && clock.instant().isBefore(establishedAt.plus(absoluteLifetime))) {
            return chain.filter(exchange)
        }

        return exchange.session.flatMap { it.invalidate() }
            .then(unauthorized(exchange))
    }

    private fun Authentication.isBffAuthentication(): Boolean = isAuthenticated && principal is ResolvedOidcUser

    private fun unauthorized(exchange: ServerWebExchange): Mono<Void> {
        exchange.response.statusCode = HttpStatus.UNAUTHORIZED
        return exchange.response.setComplete()
    }

    companion object {
        internal const val AUTHENTICATED_AT_ATTRIBUTE = "bff.authenticated-at"
        private const val BFF_SESSION_ABSOLUTE_HOURS = 8L
    }
}

class BffLoginSuccessHandler(publicAppUrl: String, adminPublicOrigin: String) : ServerAuthenticationSuccessHandler {
    private val appOrigin = appOwnedOrigin(publicAppUrl)
    private val adminOrigin = adminPublicOrigin.takeIf(String::isNotBlank)?.let(::appOwnedOrigin)

    override fun onAuthenticationSuccess(
        webFilterExchange: WebFilterExchange,
        authentication: Authentication,
    ): Mono<Void> {
        val exchange = webFilterExchange.exchange
        val clientId = (authentication as? OAuth2AuthenticationToken)?.authorizedClientRegistrationId
        val origin = when (clientId) {
            APP_OIDC_CLIENT_ID -> appOrigin
            ADMIN_OIDC_CLIENT_ID -> requireNotNull(adminOrigin) { "Admin public origin is not configured" }
            else -> throw IllegalArgumentException("Unsupported OIDC client registration: $clientId")
        }
        return exchange.session.flatMap { session ->
            val returnPath = session.attributes.remove(returnPathAttribute(clientId)) as? String
            val redirectLocation = safeReturnPath(returnPath)?.let(origin::resolve) ?: origin
            exchange.response.statusCode = HttpStatus.SEE_OTHER
            exchange.response.headers.location = redirectLocation
            exchange.response.setComplete()
        }
    }

    private fun safeReturnPath(value: String?): URI? {
        if (value.isNullOrBlank() || '\\' in value || ENCODED_SEPARATOR.containsMatchIn(value)) return null
        val path = try {
            URI.create(value)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val rawPath = path.rawPath.orEmpty()
        if (path.isAbsolute || path.rawAuthority != null || !rawPath.startsWith("/") || rawPath.startsWith("//")) {
            return null
        }
        return path
    }

    private fun appOwnedOrigin(configuredUrl: String): URI {
        val configuredUri = URI.create(configuredUrl)
        require(
            configuredUri.scheme == "https" &&
                configuredUri.host != null &&
                configuredUri.userInfo == null &&
                configuredUri.path in listOf("", "/") &&
                configuredUri.query == null &&
                configuredUri.fragment == null,
        )
        return URI(configuredUri.scheme, null, configuredUri.host, configuredUri.port, "/", null, null)
    }

    private fun returnPathAttribute(clientId: String?): String = "$RETURN_PATH_ATTRIBUTE_PREFIX.$clientId"

    private companion object {
        const val APP_OIDC_CLIENT_ID = "pt-app"
        const val ADMIN_OIDC_CLIENT_ID = "pt-admin"
        const val RETURN_PATH_ATTRIBUTE_PREFIX = "bff.oidc.return-path"
        val ENCODED_SEPARATOR = Regex("%(?:25)*(?:2f|5c)", RegexOption.IGNORE_CASE)
    }
}

class BffLoginReturnPathCaptureWebFilter : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val path = exchange.request.path.pathWithinApplication().value()
        val clientId = OIDC_CLIENT_IDS.firstOrNull { path == "$AUTHORIZATION_PATH_PREFIX$it" }
        if (clientId !in OIDC_CLIENT_IDS) return chain.filter(exchange)

        val returnPath = safeReturnPath(exchange.request.queryParams.getFirst(RETURN_TO_PARAMETER))
        return exchange.session.flatMap { session ->
            if (returnPath == null) {
                session.attributes.remove("$RETURN_PATH_ATTRIBUTE_PREFIX.$clientId")
            } else {
                session.attributes["$RETURN_PATH_ATTRIBUTE_PREFIX.$clientId"] = returnPath
            }
            chain.filter(exchange)
        }
    }

    private fun safeReturnPath(value: String?): String? {
        if (value.isNullOrBlank() || '\\' in value || ENCODED_SEPARATOR.containsMatchIn(value)) return null
        val path = try {
            URI.create(value)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val rawPath = path.rawPath.orEmpty()
        if (path.isAbsolute || path.rawAuthority != null || !rawPath.startsWith("/") || rawPath.startsWith("//")) {
            return null
        }
        return value
    }

    private companion object {
        const val AUTHORIZATION_PATH_PREFIX = "/oauth2/authorization/"
        const val RETURN_TO_PARAMETER = "returnTo"
        const val RETURN_PATH_ATTRIBUTE_PREFIX = "bff.oidc.return-path"
        val OIDC_CLIENT_IDS = setOf("pt-app", "pt-admin")
        val ENCODED_SEPARATOR = Regex("%(?:25)*(?:2f|5c)", RegexOption.IGNORE_CASE)
    }
}

private const val BFF_SESSION_ABSOLUTE_HOURS = 8L
