package com.profiletailors.smp.identity.application

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.identity.domain.PrincipalIdentityFacts
import com.profiletailors.smp.identity.domain.PrincipalStatus
import com.profiletailors.smp.identity.domain.UserAccountState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExternalIdentityLinkProvisioningServiceTest {
    private val lookup = mockk<PrincipalIdentityLookup>()
    private val gateway = mockk<ExternalIdentityLinkGateway>()
    private val service = ExternalIdentityLinkProvisioningService(gateway, lookup, ISSUER)
    private val principalId = "00000000-0000-0000-0000-000000000001"

    @Test
    fun `provisions only the explicit pair for an existing principal`() = runTest {
        coEvery { lookup.findByPrincipalId(principalId) } returns existingPrincipal()
        coEvery { gateway.create(ISSUER, SUBJECT, principalId) } returns Unit

        service.provision(principalId, ISSUER, SUBJECT)

        coVerify(exactly = 1) { gateway.create(ISSUER, SUBJECT, principalId) }
    }

    @Test
    fun `rejects an issuer outside the configured Keycloak issuer without mutation`() = runTest {
        try {
            service.provision(principalId, "https://attacker.example/realm", SUBJECT)
            error("Expected issuer validation to fail")
        } catch (exception: IllegalArgumentException) {
            assertEquals("Failed requirement.", exception.message)
        }
        coVerify(exactly = 0) { gateway.create(any(), any(), any()) }
    }

    @Test
    fun `rejects blank or oversized subjects without creating a link`() = runTest {
        listOf("", " ", "x".repeat(256)).forEach { subject ->
            val failure = runCatching { service.provision(principalId, ISSUER, subject) }.exceptionOrNull()
            assertEquals(IllegalArgumentException::class.java, failure?.javaClass)
        }

        coVerify(exactly = 0) { gateway.create(any(), any(), any()) }
    }

    @Test
    fun `rejects a missing principal without creating a link`() = runTest {
        coEvery { lookup.findByPrincipalId(principalId) } returns null

        val error = try {
            service.provision(principalId, ISSUER, SUBJECT)
            error("Expected a missing principal to fail")
        } catch (exception: ExternalIdentityLinkTargetNotFoundException) {
            exception
        }

        assertEquals("Target principal was not found.", error.message)
        coVerify(exactly = 0) { gateway.create(any(), any(), any()) }
    }

    private fun existingPrincipal() = PrincipalIdentityFacts(
        principalId = principalId,
        principalType = PrincipalType.USER,
        subject = "local-subject",
        provider = "local-provider",
        displayIdentity = "Local user",
        email = "local@example.test",
        username = "local-user",
        emailStatus = null,
        status = PrincipalStatus.ACTIVE,
        accountState = UserAccountState.ACTIVE,
    )

    private companion object {
        const val ISSUER = "https://keycloak.ahome.quest/realms/pt"
        const val SUBJECT = "keycloak-subject"
    }
}
