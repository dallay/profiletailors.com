package com.profiletailors.smp.identity.infrastructure.security

import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.time.Clock
import java.time.Duration
import java.time.Instant

class AdminAndFreshMfaAuthorizationWebFilter(
    private val expectedIssuer: String,
    private val clock: () -> Instant = { Clock.systemUTC().instant() },
) : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> =
        ReactiveSecurityContextHolder.getContext()
            .defaultIfEmpty(org.springframework.security.core.context.SecurityContextHolder.createEmptyContext())
            .flatMap { context ->
                val authentication = context.authentication
                if (
                    authentication != null &&
                    authentication.isAuthenticated &&
                    authentication !is AnonymousAuthenticationToken
                ) {
                    authorize(exchange, chain, authentication.principal)
                } else {
                    chain.filter(exchange)
                }
            }

    private fun authorize(exchange: ServerWebExchange, chain: WebFilterChain, principal: Any?): Mono<Void> {
        val token = (principal as? ResolvedOidcUser)?.idToken
        val path = exchange.request.path.pathWithinApplication().value()
        val adminRoute = path == ADMIN_ROOT || path.startsWith(ADMIN_PREFIX)
        val adminAllowed = !adminRoute || principal !is ResolvedOidcUser || token?.isAdminIdentity() == true
        val identityLinkAllowed = !isIdentityLinkRequest(exchange) ||
            principal is ResolvedOidcUser &&
            token?.isAdminIdentity() == true
        val mutationAllowed =
            !isCriticalMutation(exchange) || principal !is ResolvedOidcUser || token?.hasFreshMfa(clock()) == true
        return if (adminAllowed && identityLinkAllowed && mutationAllowed) chain.filter(exchange) else deny(exchange)
    }

    private fun isIdentityLinkRequest(exchange: ServerWebExchange): Boolean =
        exchange.request.method.name() == HTTP_POST &&
            IDENTITY_LINK_ROUTE.matches(exchange.request.path.pathWithinApplication())

    private fun isCriticalMutation(exchange: ServerWebExchange): Boolean {
        val method = exchange.request.method
        if (method.name() in SAFE_METHODS) return false
        return CRITICAL_MUTATIONS.any { (routeMethod, pattern) ->
            routeMethod == method.name() && pattern.matches(exchange.request.path.pathWithinApplication())
        }
    }

    private fun deny(exchange: ServerWebExchange): Mono<Void> {
        exchange.response.statusCode = HttpStatus.FORBIDDEN
        return exchange.response.setComplete()
    }

    private fun OidcIdToken.isAdminIdentity(): Boolean = issuer?.toString() == expectedIssuer &&
        !subject.isNullOrBlank() &&
        ADMIN_CLIENT_ID in audience &&
        getClaimAsString(AUTHORIZED_PARTY) == ADMIN_CLIENT_ID &&
        getClaimAsString(ACR) == ADMIN_ACR

    private fun OidcIdToken.hasFreshMfa(now: Instant): Boolean {
        val authTime = claims[IdTokenClaimNames.AUTH_TIME] as? Instant ?: return false
        val age = Duration.between(authTime, now)
        val amr = claims[AMR] as? Collection<*> ?: return false
        return !age.isNegative && age <= MAX_AUTHENTICATION_AGE && amr.any { it in MFA_METHODS }
    }

    private companion object {
        const val ADMIN_PREFIX = "/api/admin/"
        const val ADMIN_ROOT = "/api/admin"
        const val ADMIN_CLIENT_ID = "pt-admin"
        const val ADMIN_ACR = "2"
        const val AUTHORIZED_PARTY = "azp"
        const val ACR = "acr"
        const val AMR = "amr"
        val SAFE_METHODS = setOf(HTTP_GET, "HEAD", "OPTIONS", "TRACE")
        val MFA_METHODS = setOf("otp", "mfa")
        const val HTTP_POST = "POST"
        const val HTTP_GET = "GET"
        const val HTTP_DELETE = "DELETE"
        const val HTTP_PATCH = "PATCH"
        val IDENTITY_LINK_ROUTE =
            org.springframework.web.util.pattern.PathPatternParser.defaultInstance
                .parse("/api/admin/users/{principalId}/identity-link")
        val MAX_AUTHENTICATION_AGE: Duration = Duration.ofSeconds(300)
        val CRITICAL_MUTATIONS = listOf(
            HTTP_POST to "/api/v1/account/close",
            HTTP_POST to "/api/tenancy/workspace-ownership/owners",
            HTTP_DELETE to "/api/tenancy/workspace-ownership/owners/{principalId}",
            HTTP_POST to "/api/tenancy/workspace-ownership/owners/transfer",
            HTTP_POST to "/api/admin/users/{principalId}/disable",
            HTTP_POST to "/api/admin/users/{principalId}/enable",
            HTTP_POST to "/api/admin/users/{principalId}/sessions/revoke",
            HTTP_POST to "/api/admin/users/{principalId}/identity-link",
            HTTP_POST to "/api/admin/operators/{principalId}/roles",
            HTTP_DELETE to "/api/admin/operators/{principalId}/roles/{role}",
            HTTP_POST to "/api/admin/configuration/registration-mode",
            HTTP_POST to "/api/admin/invitations/{invitationId}/resend",
            HTTP_POST to "/api/admin/invitations/{invitationId}/revoke",
            HTTP_POST to "/api/admin/invitations/direct",
            HTTP_POST to "/api/admin/invitations/{invitationId}/direct-revoke",
            HTTP_POST to "/api/admin/invitations/{invitationId}/direct-resend",
            HTTP_POST to "/api/admin/waitlist-entries/{entryId}/invitations",
            HTTP_POST to "/api/admin/waitlist-entries/{entryId}/cancel",
            HTTP_POST to "/api/admin/waitlist-entries/invitations:bulk",
            HTTP_POST to "/api/admin/takedown-reports/{reportId}/approve",
            HTTP_POST to "/api/admin/takedown-reports/{reportId}/reject",
            HTTP_POST to "/api/publishing/{provider}/connections/initiate",
            HTTP_POST to "/api/publishing/{provider}/connections/complete",
            HTTP_DELETE to "/api/publishing/{provider}/connections/{connectionId}",
            HTTP_POST to "/api/publishing/linkedin/connections/initiate",
            HTTP_POST to "/api/publishing/linkedin/connections/complete",
            HTTP_POST to "/api/publishing/publications",
            HTTP_POST to "/api/publishing/publications/batch",
            HTTP_POST to "/api/publishing/publications/{publicationId}/cancel",
            HTTP_DELETE to "/api/publishing/publications/{publicationId}",
            HTTP_POST to "/api/publishing/publications/{publicationId}/retry",
            HTTP_POST to "/api/publishing/publications/quick-create",
            HTTP_PATCH to "/api/publishing/publications/{publicationId}/reschedule",
            HTTP_POST to "/api/v1/workspaces/{workspaceId}/recurring",
            HTTP_PATCH to "/api/v1/workspaces/{workspaceId}/recurring/{id}",
            HTTP_DELETE to "/api/v1/workspaces/{workspaceId}/recurring/{id}",
            HTTP_POST to "/api/v1/workspaces/{workspaceId}/bulk/schedule",
        ).map { (method, pattern) ->
            method to org.springframework.web.util.pattern.PathPatternParser.defaultInstance.parse(pattern)
        }
    }
}
