package com.profiletailors.smp.identity.application

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.identity.domain.EmailStatus
import com.profiletailors.smp.identity.domain.PrincipalIdentityFacts
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GetCurrentUserProfileServiceTest {

    @Test
    fun `returns current user profile from principal context and persisted identity`() = runTest {
        val service = GetCurrentUserProfileService(
            principalContextProvider = PrincipalContextProviderStub(
                PrincipalContext(
                    principalId = "user-1",
                    principalType = PrincipalType.USER,
                    subject = "local:yuniel@example.com",
                    provider = null,
                    displayIdentity = "yuniel",
                    attributes = mapOf(
                        "email" to "yuniel@example.com",
                        "preferred_username" to "yuniel",
                    ),
                ),
            ),
            principalIdentityLookup = PrincipalIdentityLookupStub(
                PrincipalIdentityFacts(
                    principalId = "user-1",
                    principalType = PrincipalType.USER,
                    subject = "local:yuniel@example.com",
                    provider = null,
                    displayIdentity = "yuniel",
                    email = "yuniel@example.com",
                    username = "yuniel",
                    emailStatus = EmailStatus.PENDING,
                ),
            ),
        )

        val profile = service.execute()

        assertEquals("user-1", profile.principalId)
        assertEquals("yuniel@example.com", profile.email)
        assertEquals("yuniel", profile.username)
        assertEquals("yuniel", profile.displayIdentity)
        assertEquals(EmailStatus.PENDING, profile.emailStatus)
    }

    @Test
    fun `returns verified status from persisted identity`() = runTest {
        val service = GetCurrentUserProfileService(
            principalContextProvider = PrincipalContextProviderStub(
                PrincipalContext(
                    principalId = "user-1",
                    principalType = PrincipalType.USER,
                    subject = "local:yuniel@example.com",
                ),
            ),
            principalIdentityLookup = PrincipalIdentityLookupStub(
                PrincipalIdentityFacts(
                    principalId = "user-1",
                    principalType = PrincipalType.USER,
                    subject = "local:yuniel@example.com",
                    provider = null,
                    displayIdentity = "yuniel",
                    email = "yuniel@example.com",
                    username = "yuniel",
                    emailStatus = EmailStatus.VERIFIED,
                ),
            ),
        )

        assertEquals(EmailStatus.VERIFIED, service.execute().emailStatus)
    }

    private class PrincipalContextProviderStub(private val principalContext: PrincipalContext) :
        PrincipalContextProvider {
        override suspend fun current(): PrincipalContext = principalContext
    }

    private class PrincipalIdentityLookupStub(private val facts: PrincipalIdentityFacts?) : PrincipalIdentityLookup {
        override suspend fun findBySubject(
            principalType: PrincipalType,
            subject: String,
            provider: String?,
        ): PrincipalIdentityFacts? = facts

        override suspend fun findByEmail(email: String): PrincipalIdentityFacts? = null

        override suspend fun findByPrincipalId(principalId: String): PrincipalIdentityFacts? = facts
    }
}
