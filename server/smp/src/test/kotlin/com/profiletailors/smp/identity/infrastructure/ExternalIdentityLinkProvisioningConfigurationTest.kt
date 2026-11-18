package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.identity.application.ExternalIdentityLinkGateway
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.domain.PrincipalIdentityFacts
import com.profiletailors.smp.identity.domain.PrincipalStatus
import com.profiletailors.smp.identity.domain.UserAccountState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class ExternalIdentityLinkProvisioningConfigurationTest {
    private val lookup = mockk<PrincipalIdentityLookup>()
    private val gateway = mockk<ExternalIdentityLinkGateway>()
    private val principalId = "00000000-0000-0000-0000-000000000001"
    private val configuredIssuer = "https://identity.example/realms/custom"

    @Test
    fun `uses the configured issuer and rejects a mismatched caller issuer`() = runTest {
        val service = IdentityBootstrapConfiguration().externalLinkProvisioner(
            gateway,
            lookup,
            configuredIssuer,
        )
        coEvery { lookup.findByPrincipalId(principalId) } returns existingPrincipal()
        coEvery { gateway.create(configuredIssuer, SUBJECT, principalId) } returns Unit

        service.provision(principalId, configuredIssuer, SUBJECT)

        coVerify(exactly = 1) { gateway.create(configuredIssuer, SUBJECT, principalId) }
        val failure = try {
            service.provision(principalId, "https://attacker.example/realm", SUBJECT)
            null
        } catch (exception: IllegalArgumentException) {
            exception
        }
        assertNotNull(failure)
        coVerify(exactly = 0) { gateway.create("https://attacker.example/realm", any(), any()) }
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
        const val SUBJECT = "configured-subject"
    }
}
