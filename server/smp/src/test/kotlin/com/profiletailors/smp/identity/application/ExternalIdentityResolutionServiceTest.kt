package com.profiletailors.smp.identity.application

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.identity.domain.EmailStatus
import com.profiletailors.smp.identity.domain.ExternalIdentityLink
import com.profiletailors.smp.identity.domain.ExternalIdentityLinkStatus
import com.profiletailors.smp.identity.domain.PrincipalIdentityFacts
import com.profiletailors.smp.identity.domain.PrincipalStatus
import com.profiletailors.smp.identity.domain.UserAccountState
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class ExternalIdentityResolutionServiceTest {
    private val principalId = UUID.randomUUID().toString()
    private val principal = PrincipalIdentityFacts(
        principalId = principalId,
        principalType = PrincipalType.USER,
        subject = "local-subject",
        provider = null,
        displayIdentity = "Local principal",
        email = "local@example.test",
        username = "local-user",
        emailStatus = EmailStatus.VERIFIED,
        status = PrincipalStatus.ACTIVE,
        accountState = UserAccountState.ACTIVE,
    )

    @Test
    fun `approved issuer subject link resolves only its existing principal and syncs verified email`() = runTest {
        val emailMatchedPrincipal = principal.copy(
            principalId = UUID.randomUUID().toString(),
            subject = "email-matched-principal",
            email = "new@example.test",
        )
        val gateway = FakeExternalIdentityLinkGateway(
            links = listOf(approvedLink(principalId)),
            emailSyncResult = VerifiedEmailSyncResult.UPDATED,
        )
        val lookup = FakePrincipalIdentityLookup(principal, emailMatchedPrincipal)

        val resolved = ExternalIdentityResolutionService(gateway, lookup).resolve(
            ValidatedExternalIdentity(ISSUER, SUBJECT, "new@example.test", true),
        )

        assertNotNull(resolved)
        assertEquals(principalId, resolved?.principal?.principalId)
        assertEquals(VerifiedEmailSyncResult.UPDATED, resolved?.emailSyncResult)
        assertEquals(listOf(ISSUER to SUBJECT), gateway.lookups)
        assertEquals(listOf(principalId to "new@example.test"), gateway.emailSyncs)
        assertEquals(listOf(principalId), lookup.principalLookups)
        assertTrue(lookup.emailLookups.isEmpty())
    }

    @Test
    fun `unverified email is not synchronized and the linked principal remains resolved`() = runTest {
        val gateway = FakeExternalIdentityLinkGateway(links = listOf(approvedLink(principalId)))
        val resolved = ExternalIdentityResolutionService(gateway, FakePrincipalIdentityLookup(principal)).resolve(
            ValidatedExternalIdentity(ISSUER, SUBJECT, "unverified@example.test", false),
        )

        assertEquals(principalId, resolved?.principal?.principalId)
        assertNull(resolved?.emailSyncResult)
        assertTrue(gateway.emailSyncs.isEmpty())
    }

    @Test
    fun `email conflict does not change the resolved linked principal`() = runTest {
        val gateway = FakeExternalIdentityLinkGateway(
            links = listOf(approvedLink(principalId)),
            emailSyncResult = VerifiedEmailSyncResult.CONFLICT,
        )
        val resolved = ExternalIdentityResolutionService(gateway, FakePrincipalIdentityLookup(principal)).resolve(
            ValidatedExternalIdentity(ISSUER, SUBJECT, "other-principal@example.test", true),
        )

        assertEquals(principalId, resolved?.principal?.principalId)
        assertEquals("local@example.test", resolved?.principal?.email)
        assertEquals(VerifiedEmailSyncResult.CONFLICT, resolved?.emailSyncResult)
    }

    @Test
    fun `missing ambiguous disabled and disallowed links fail closed`() = runTest {
        val cases = listOf(
            emptyList(),
            listOf(approvedLink(principalId), approvedLink(UUID.randomUUID().toString())),
            listOf(approvedLink(principalId, ExternalIdentityLinkStatus.DISABLED)),
            listOf(approvedLink(principalId, ExternalIdentityLinkStatus.DISALLOWED)),
        )

        cases.forEach { links ->
            val gateway = FakeExternalIdentityLinkGateway(links)
            val resolved = ExternalIdentityResolutionService(gateway, FakePrincipalIdentityLookup(principal)).resolve(
                ValidatedExternalIdentity(ISSUER, SUBJECT, "other@example.test", true),
            )

            assertNull(resolved)
            assertTrue(gateway.emailSyncs.isEmpty())
        }
    }

    @Test
    fun `local authentication and account ineligibility deny resolution`() = runTest {
        val ineligiblePrincipals = listOf(
            principal.copy(status = PrincipalStatus.SUSPENDED),
            principal.copy(accountState = UserAccountState.DISABLED),
        )

        ineligiblePrincipals.forEach { ineligible ->
            val gateway = FakeExternalIdentityLinkGateway(links = listOf(approvedLink(principalId)))
            val resolved = ExternalIdentityResolutionService(
                gateway,
                FakePrincipalIdentityLookup(ineligible),
            ).resolve(ValidatedExternalIdentity(ISSUER, SUBJECT, null, false))

            assertNull(resolved)
            assertTrue(gateway.emailSyncs.isEmpty())
        }
    }

    private fun approvedLink(
        linkedPrincipalId: String,
        status: ExternalIdentityLinkStatus = ExternalIdentityLinkStatus.APPROVED,
    ) = ExternalIdentityLink(ISSUER, SUBJECT, linkedPrincipalId, status)

    private class FakeExternalIdentityLinkGateway(
        private val links: List<ExternalIdentityLink>,
        private val emailSyncResult: VerifiedEmailSyncResult = VerifiedEmailSyncResult.UNCHANGED,
    ) : ExternalIdentityLinkGateway {
        val lookups = mutableListOf<Pair<String, String>>()
        val emailSyncs = mutableListOf<Pair<String, String>>()

        override suspend fun findByIssuerAndSubject(issuer: String, subject: String): List<ExternalIdentityLink> {
            lookups += issuer to subject
            return links
        }

        override suspend fun synchronizeVerifiedEmail(principalId: String, email: String): VerifiedEmailSyncResult {
            emailSyncs += principalId to email
            return emailSyncResult
        }
    }

    private class FakePrincipalIdentityLookup(
        private val principal: PrincipalIdentityFacts?,
        private val emailMatchedPrincipal: PrincipalIdentityFacts? = null,
    ) : PrincipalIdentityLookup {
        val principalLookups = mutableListOf<String>()
        val emailLookups = mutableListOf<String>()

        override suspend fun findBySubject(
            principalType: PrincipalType,
            subject: String,
            provider: String?,
        ): PrincipalIdentityFacts? = null

        override suspend fun findByEmail(email: String): PrincipalIdentityFacts? {
            emailLookups += email
            return emailMatchedPrincipal
        }

        override suspend fun findByPrincipalId(principalId: String): PrincipalIdentityFacts? {
            principalLookups += principalId
            return principal
        }
    }

    companion object {
        private const val ISSUER = "https://keycloak.example.test/realms/pt"
        private const val SUBJECT = "keycloak-subject-1"
    }
}
