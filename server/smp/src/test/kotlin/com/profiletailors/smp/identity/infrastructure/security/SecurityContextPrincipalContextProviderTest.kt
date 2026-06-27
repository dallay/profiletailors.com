package com.profiletailors.smp.identity.infrastructure.security

import com.profiletailors.common.domain.context.MissingPrincipalContextException
import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.credentials.domain.CredentialType
import com.profiletailors.smp.identity.domain.AuthenticatedPrincipal
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import reactor.core.publisher.Mono

class SecurityContextPrincipalContextProviderTest {

    private val provider = SecurityContextPrincipalContextProvider()

    @Test
    fun `returns repo local principal from reactive security context`() = runTest {
        val principal = AuthenticatedPrincipal(
            context = PrincipalContext(
                principalId = "principal-1",
                principalType = PrincipalType.USER,
                subject = "user-123",
                provider = "https://issuer.example",
            ),
            credentialType = CredentialType.JWT,
        )
        val authentication = TestingAuthenticationToken(principal, "token").apply { isAuthenticated = true }

        val resolved = Mono.defer {
            kotlinx.coroutines.reactor.mono {
                provider.require()
            }
        }
            .contextWrite(
                ReactiveSecurityContextHolder.withSecurityContext(Mono.just(SecurityContextImpl(authentication))),
            )
            .awaitSingle()

        assertEquals(principal.context, resolved)
    }

    @Test
    fun `fails when security context does not expose repo local principal`() = runTest {
        val authentication = TestingAuthenticationToken("raw-principal", "token").apply { isAuthenticated = true }

        val error = assertThrows(MissingPrincipalContextException::class.java) {
            kotlinx.coroutines.runBlocking {
                Mono.defer {
                    kotlinx.coroutines.reactor.mono {
                        provider.require()
                    }
                }
                    .contextWrite(
                        ReactiveSecurityContextHolder.withSecurityContext(
                            Mono.just(SecurityContextImpl(authentication)),
                        ),
                    )
                    .awaitSingle()
            }
        }

        assertEquals("Authenticated principal context is required.", error.message)
    }
}
