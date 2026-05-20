package com.profiletailors.smp.identity.infrastructure.security

import com.profiletailors.smp.credentials.application.ActiveApiKeyCredential
import com.profiletailors.smp.identity.domain.AuthenticatedPrincipal
import com.profiletailors.smp.identity.domain.PrincipalContext
import com.profiletailors.smp.identity.domain.PrincipalType
import com.profiletailors.smp.identity.infrastructure.ApiKeyAuthenticatedPrincipalMaterializer
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.web.server.ServerAuthenticationEntryPoint
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

class ApiKeyAuthenticationWebFilterTest {

    @Test
    fun `authenticates proving slice api key request and exposes authentication downstream`() = runTest {
        val filter = ApiKeyAuthenticationWebFilter(
            ApiKeyPrincipalAuthenticationConverter(
                apiKeyCredentialStateLookup = StubApiKeyCredentialStateLookup(),
                principalMaterializer = StubApiKeyAuthenticatedPrincipalMaterializer(),
            ),
            unauthorizedEntryPoint(),
        )
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get(IdentitySecurityConfiguration.WORKSPACE_ACCESS_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ptk_lookup.secret-value")
                .build(),
        )
        var principalId: String? = null

        filter.filter(exchange, WebFilterChain { _ ->
            ReactiveSecurityContextHolder.getContext()
                .doOnNext { context ->
                    val principal = context.authentication?.principal as? AuthenticatedPrincipal
                    principalId = principal?.context?.principalId
                }
                .then(Mono.empty())
        }).block()

        assertEquals("api-key-principal-1", principalId)
    }

    @Test
    fun `falls through for jwt bearer value on proving slice`() = runTest {
        val filter = ApiKeyAuthenticationWebFilter(
            ApiKeyPrincipalAuthenticationConverter(
                apiKeyCredentialStateLookup = StubApiKeyCredentialStateLookup(),
                principalMaterializer = StubApiKeyAuthenticatedPrincipalMaterializer(),
            ),
            unauthorizedEntryPoint(),
        )
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get(IdentitySecurityConfiguration.WORKSPACE_ACCESS_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build(),
        )
        var authSeen = false

        filter.filter(exchange, WebFilterChain { _ ->
            ReactiveSecurityContextHolder.getContext()
                .doOnNext { authSeen = true }
                .switchIfEmpty(Mono.fromRunnable { authSeen = false })
                .then(Mono.empty())
        }).block()

        assertEquals(false, authSeen)
    }

    @Test
    fun `returns unauthorized when api key authentication fails`() = runTest {
        val filter = ApiKeyAuthenticationWebFilter(
            ApiKeyPrincipalAuthenticationConverter(
                apiKeyCredentialStateLookup = object : com.profiletailors.smp.credentials.application.ApiKeyCredentialStateLookup {
                    override suspend fun requireActive(presentedApiKey: String): ActiveApiKeyCredential {
                        throw com.profiletailors.smp.credentials.application.ApiKeyCredentialNotActiveException(
                            credentialReference = "api-key-cred-1",
                            principalId = "api-key-principal-1",
                            reason = com.profiletailors.smp.credentials.application.ApiKeyCredentialFailureReason.REVOKED,
                        )
                    }
                },
                principalMaterializer = StubApiKeyAuthenticatedPrincipalMaterializer(),
            ),
            unauthorizedEntryPoint(),
        )
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get(IdentitySecurityConfiguration.WORKSPACE_ACCESS_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ptk_lookup.secret-value")
                .build(),
        )

        filter.filter(exchange, WebFilterChain { Mono.empty() }).block()

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
    }

    private class StubApiKeyCredentialStateLookup : com.profiletailors.smp.credentials.application.ApiKeyCredentialStateLookup {
        override suspend fun requireActive(presentedApiKey: String): ActiveApiKeyCredential = ActiveApiKeyCredential(
            principalId = "api-key-principal-1",
            credentialReference = "api-key-cred-1",
            subject = "api-key-subject",
            provider = null,
        )
    }

    private fun unauthorizedEntryPoint(): ServerAuthenticationEntryPoint = ServerAuthenticationEntryPoint { exchange, _ ->
        exchange.response.statusCode = HttpStatus.UNAUTHORIZED
        exchange.response.setComplete()
    }

    private class StubApiKeyAuthenticatedPrincipalMaterializer : ApiKeyAuthenticatedPrincipalMaterializer(
        principalIdentityLookup = object : com.profiletailors.smp.identity.application.PrincipalIdentityLookup {
            override suspend fun findBySubject(
                principalType: PrincipalType,
                subject: String,
                provider: String?,
            ) = null
        },
    ) {
        override suspend fun materialize(activeCredential: ActiveApiKeyCredential): AuthenticatedPrincipal = AuthenticatedPrincipal(
            context = PrincipalContext(
                principalId = activeCredential.principalId,
                principalType = PrincipalType.API_KEY,
                subject = activeCredential.subject,
                provider = activeCredential.provider,
                displayIdentity = "integration-key",
                authenticationMethod = "API_KEY",
                issuedCredentialReference = activeCredential.credentialReference,
            ),
            credentialType = com.profiletailors.smp.credentials.domain.CredentialType.API_KEY,
        )
    }
}
