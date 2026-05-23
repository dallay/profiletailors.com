package com.profiletailors.smp.identity.infrastructure.security

import com.profiletailors.smp.credentials.application.ActiveApiKeyCredential
import com.profiletailors.smp.credentials.application.ApiKeyCredentialFailureReason
import com.profiletailors.smp.credentials.application.ApiKeyCredentialNotActiveException
import com.profiletailors.smp.credentials.application.ApiKeyCredentialStateLookup
import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.identity.domain.AuthenticatedPrincipal
import com.profiletailors.smp.identity.infrastructure.ApiKeyAuthenticatedPrincipalMaterializer
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ApiKeyPrincipalAuthenticationConverterTest {

    @Test
    fun `converts api key bearer value into authenticated principal token`() = runTest {
        val converter = ApiKeyPrincipalAuthenticationConverter(
            apiKeyCredentialStateLookup = StubApiKeyCredentialStateLookup(),
            principalMaterializer = StubApiKeyAuthenticatedPrincipalMaterializer(),
        )

        val authentication = converter.convert("ptk_lookup.secret-value").awaitSingle()

        val principal = authentication.principal as AuthenticatedPrincipal
        assertEquals(PrincipalType.API_KEY, principal.context.principalType)
        assertEquals("api-key-principal-1", principal.context.principalId)
        assertEquals("api-key-subject", authentication.name)
        assertEquals("ptk_lookup.secret-value", authentication.credentials)
    }

    @Test
    fun `propagates revoked api key failures`() = runTest {
        val converter = ApiKeyPrincipalAuthenticationConverter(
            apiKeyCredentialStateLookup = object : ApiKeyCredentialStateLookup {
                override suspend fun requireActive(presentedApiKey: String): ActiveApiKeyCredential {
                    throw ApiKeyCredentialNotActiveException(
                        credentialReference = "api-key-cred-1",
                        principalId = "api-key-principal-1",
                        reason = ApiKeyCredentialFailureReason.REVOKED,
                    )
                }
            },
            principalMaterializer = StubApiKeyAuthenticatedPrincipalMaterializer(),
        )

        val error = assertThrows(ApiKeyCredentialNotActiveException::class.java) {
            kotlinx.coroutines.runBlocking {
                converter.convert("ptk_lookup.secret-value").awaitSingle()
            }
        }

        assertEquals(ApiKeyCredentialFailureReason.REVOKED, error.reason)
    }

    private class StubApiKeyCredentialStateLookup : ApiKeyCredentialStateLookup {
        override suspend fun requireActive(presentedApiKey: String): ActiveApiKeyCredential = ActiveApiKeyCredential(
            principalId = "api-key-principal-1",
            credentialReference = "api-key-cred-1",
            subject = "api-key-subject",
            provider = null,
        )
    }

    private class StubApiKeyAuthenticatedPrincipalMaterializer : ApiKeyAuthenticatedPrincipalMaterializer(
        principalIdentityLookup = object : com.profiletailors.smp.identity.application.PrincipalIdentityLookup {
            override suspend fun findBySubject(
                principalType: PrincipalType,
                subject: String,
                provider: String?,
            ) = null

            override suspend fun findByEmail(email: String) = null
            override suspend fun findByPrincipalId(principalId: String) = null
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
