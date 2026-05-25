package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.credentials.application.ActiveServiceAccountCredential
import com.profiletailors.smp.credentials.application.ServiceAccountCredentialFailureReason
import com.profiletailors.smp.credentials.application.ServiceAccountCredentialNotActiveException
import com.profiletailors.smp.credentials.application.ServiceAccountCredentialStateLookup
import com.profiletailors.smp.credentials.domain.CredentialType
import com.profiletailors.smp.credentials.domain.ValidatedToken
import com.profiletailors.smp.identity.application.NoOpPrincipalIdentityLookup
import com.profiletailors.smp.identity.application.PrincipalIdentityFacts
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.identity.domain.AuthenticatedPrincipal
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant

class JwtAuthenticatedPrincipalMaterializerTest {

    @Test
    fun `materializes user principal from validated jwt token`() = runTest {
        val materializer = JwtAuthenticatedPrincipalMaterializer()
        val token = ValidatedToken(
            credentialType = CredentialType.JWT,
            tokenValue = "token-value",
            subject = "user-123",
            issuer = "https://issuer.example",
            audience = setOf("profiletailors-api"),
            issuedAt = Instant.parse("2026-05-15T10:15:30Z"),
            expiresAt = Instant.parse("2026-05-15T11:15:30Z"),
            tokenId = "jwt-1",
            claims = mapOf(
                "preferred_username" to "yuniel",
                "email" to "yuniel@example.com",
            ),
        )

        val authenticatedPrincipal = materializer.materialize(token)

        assertEquals(PrincipalType.USER, authenticatedPrincipal.context.principalType)
        assertEquals("user-123", authenticatedPrincipal.context.principalId)
        assertEquals("user-123", authenticatedPrincipal.context.subject)
        assertEquals("https://issuer.example", authenticatedPrincipal.context.provider)
        assertEquals("yuniel", authenticatedPrincipal.context.displayIdentity)
        assertEquals("JWT_BEARER", authenticatedPrincipal.context.authenticationMethod)
        assertEquals("jwt-1", authenticatedPrincipal.context.issuedCredentialReference)
        assertEquals("yuniel@example.com", authenticatedPrincipal.context.attributes["email"])
        assertEquals(CredentialType.JWT, authenticatedPrincipal.credentialType)
    }

    @Test
    fun `materializes service-account principal when credential is active`() = runTest {
        val materializer = JwtAuthenticatedPrincipalMaterializer(
            principalIdentityLookup = StubPrincipalIdentityLookup(
                PrincipalIdentityFacts(
                    principalId = "service-principal-1",
                    principalType = PrincipalType.SERVICE_ACCOUNT,
                    subject = "service-account-subject",
                    provider = "https://issuer.example",
                    displayIdentity = "scheduler-bot",
                    email = null,
                    username = null,
                ),
            ),
            serviceAccountCredentialStateLookup = StubServiceAccountCredentialStateLookup(
                ActiveServiceAccountCredential(
                    principalId = "service-principal-1",
                    credentialReference = "svc-cred-1",
                ),
            ),
        )
        val token = ValidatedToken(
            credentialType = CredentialType.SERVICE_ACCOUNT,
            tokenValue = "service-token",
            subject = "service-account-subject",
            issuer = "https://issuer.example",
            audience = setOf("profiletailors-api"),
            issuedAt = Instant.parse("2026-05-15T10:15:30Z"),
            expiresAt = Instant.parse("2026-05-15T11:15:30Z"),
            tokenId = "jwt-service-1",
            claims = mapOf("principal_type" to "SERVICE_ACCOUNT"),
            principalTypeHint = PrincipalType.SERVICE_ACCOUNT,
            credentialReference = "svc-cred-1",
        )

        val authenticatedPrincipal = materializer.materialize(token)

        assertEquals(PrincipalType.SERVICE_ACCOUNT, authenticatedPrincipal.context.principalType)
        assertEquals("service-principal-1", authenticatedPrincipal.context.principalId)
        assertEquals("scheduler-bot", authenticatedPrincipal.context.displayIdentity)
        assertEquals("svc-cred-1", authenticatedPrincipal.context.issuedCredentialReference)
        assertEquals(CredentialType.SERVICE_ACCOUNT, authenticatedPrincipal.credentialType)
    }

    @Test
    fun `rejects service-account principal when credential is revoked`() = runTest {
        val materializer = JwtAuthenticatedPrincipalMaterializer(
            principalIdentityLookup = StubPrincipalIdentityLookup(),
            serviceAccountCredentialStateLookup = object : ServiceAccountCredentialStateLookup {
                override suspend fun requireActive(
                    credentialReference: String,
                    subject: String,
                    provider: String,
                ): ActiveServiceAccountCredential {
                    throw ServiceAccountCredentialNotActiveException(
                        credentialReference = credentialReference,
                        subject = subject,
                        provider = provider,
                        principalId = "service-principal-1",
                        reason = ServiceAccountCredentialFailureReason.REVOKED,
                    )
                }
            },
        )
        val token = serviceAccountToken()

        val error = assertThrows(ServiceAccountCredentialNotActiveException::class.java) {
            kotlinx.coroutines.runBlocking {
                materializer.materialize(token)
            }
        }

        assertEquals(ServiceAccountCredentialFailureReason.REVOKED, error.reason)
        assertEquals("service-principal-1", error.principalId)
    }

    @Test
    fun `rejects service-account principal when credential reference is missing`() = runTest {
        val materializer = JwtAuthenticatedPrincipalMaterializer(
            principalIdentityLookup = StubPrincipalIdentityLookup(),
            serviceAccountCredentialStateLookup = StubServiceAccountCredentialStateLookup(
                ActiveServiceAccountCredential(
                    principalId = "service-principal-1",
                    credentialReference = "svc-cred-1",
                ),
            ),
        )
        val token = serviceAccountToken().copy(credentialReference = null)

        val error = assertThrows(ServiceAccountCredentialNotActiveException::class.java) {
            kotlinx.coroutines.runBlocking {
                materializer.materialize(token)
            }
        }

        assertEquals(ServiceAccountCredentialFailureReason.MISSING, error.reason)
    }

    private fun serviceAccountToken() = ValidatedToken(
        credentialType = CredentialType.SERVICE_ACCOUNT,
        tokenValue = "service-token",
        subject = "service-account-subject",
        issuer = "https://issuer.example",
        audience = setOf("profiletailors-api"),
        issuedAt = Instant.parse("2026-05-15T10:15:30Z"),
        expiresAt = Instant.parse("2026-05-15T11:15:30Z"),
        tokenId = "jwt-service-1",
        claims = mapOf("principal_type" to "SERVICE_ACCOUNT"),
        principalTypeHint = PrincipalType.SERVICE_ACCOUNT,
        credentialReference = "svc-cred-1",
    )

    private class StubPrincipalIdentityLookup(
        private val facts: PrincipalIdentityFacts? = null,
    ) : PrincipalIdentityLookup {
        override suspend fun findBySubject(
            principalType: PrincipalType,
            subject: String,
            provider: String?,
        ): PrincipalIdentityFacts? = facts

        override suspend fun findByEmail(email: String): PrincipalIdentityFacts? = null

        override suspend fun findByPrincipalId(principalId: String): PrincipalIdentityFacts? = null
    }

    private class StubServiceAccountCredentialStateLookup(
        private val credential: ActiveServiceAccountCredential,
    ) : ServiceAccountCredentialStateLookup {
        override suspend fun requireActive(
            credentialReference: String,
            subject: String,
            provider: String,
        ): ActiveServiceAccountCredential = credential
    }
}
