package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.common.domain.context.MissingPrincipalContextException
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.credentials.application.ActiveApiKeyCredential
import com.profiletailors.smp.credentials.domain.CredentialType
import com.profiletailors.smp.identity.application.PrincipalIdentityFacts
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ApiKeyAuthenticatedPrincipalMaterializerTest {

    @Test
    fun `materializes api key principal from active credential`() = runTest {
        val materializer = ApiKeyAuthenticatedPrincipalMaterializer(
            principalIdentityLookup = StubPrincipalIdentityLookup(
                PrincipalIdentityFacts(
                    principalId = "api-key-principal-1",
                    principalType = PrincipalType.API_KEY,
                    subject = "api-key-subject",
                    provider = null,
                    displayIdentity = "integration-key",
                    email = null,
                    username = null,
                ),
            ),
        )

        val principal = materializer.materialize(
            ActiveApiKeyCredential(
                principalId = "api-key-principal-1",
                credentialReference = "api-key-cred-1",
                subject = "api-key-subject",
                provider = null,
            ),
        )

        assertEquals(PrincipalType.API_KEY, principal.context.principalType)
        assertEquals("api-key-principal-1", principal.context.principalId)
        assertEquals("API_KEY", principal.context.authenticationMethod)
        assertEquals("api-key-cred-1", principal.context.issuedCredentialReference)
        assertEquals(CredentialType.API_KEY, principal.credentialType)
    }

    @Test
    fun `rejects materialization when persisted principal is missing`() = runTest {
        val materializer = ApiKeyAuthenticatedPrincipalMaterializer(
            principalIdentityLookup = StubPrincipalIdentityLookup(),
        )

        val error = assertThrows(MissingPrincipalContextException::class.java) {
            kotlinx.coroutines.runBlocking {
                materializer.materialize(
                    ActiveApiKeyCredential(
                        principalId = "api-key-principal-1",
                        credentialReference = "api-key-cred-1",
                        subject = "api-key-subject",
                        provider = null,
                    ),
                )
            }
        }

        assertEquals("Authenticated API-key principal could not be materialized.", error.message)
    }

    private class StubPrincipalIdentityLookup(private val facts: PrincipalIdentityFacts? = null) :
        PrincipalIdentityLookup {
        override suspend fun findBySubject(
            principalType: PrincipalType,
            subject: String,
            provider: String?,
        ): PrincipalIdentityFacts? = facts

        override suspend fun findByEmail(email: String): PrincipalIdentityFacts? = null

        override suspend fun findByPrincipalId(principalId: String): PrincipalIdentityFacts? = null
    }
}
