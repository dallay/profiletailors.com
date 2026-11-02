package com.profiletailors.smp.platformadmin.infrastructure

import com.profiletailors.smp.governance.application.CheckBootstrapTermsConsentHandler
import com.profiletailors.smp.governance.application.CheckBootstrapTermsConsentQuery
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class GovernanceBootstrapTermsConsentVerifierTest {
    @Test
    fun `delegates terms consent check to governance application`() = runTest {
        val principalId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val query = CheckBootstrapTermsConsentQuery(principalId, "terms-v1.0.0")
        val handler = mockk<CheckBootstrapTermsConsentHandler>()
        coEvery { handler.handle(query) } returns true

        val result = GovernanceBootstrapTermsConsentVerifier(handler).hasCurrentTermsConsent(
            principalId,
            "terms-v1.0.0",
        )

        assertTrue(result)
        coVerify(exactly = 1) { handler.handle(query) }
    }
}
