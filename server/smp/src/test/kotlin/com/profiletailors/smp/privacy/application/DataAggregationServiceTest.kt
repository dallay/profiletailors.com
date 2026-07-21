package com.profiletailors.smp.privacy.application

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class DataAggregationServiceTest {

    private val identityPort = mockk<IdentityDataPort>()
    private val credentialsPort = mockk<CredentialsDataPort>()
    private val tenancyPort = mockk<TenancyAggregationPort>()
    private val publishingPort = mockk<PublishingDataPort>()
    private val mediaPort = mockk<MediaDataPort>()
    private val governancePort = mockk<GovernanceDataPort>()
    private val leadCapturePort = mockk<LeadCaptureDataPort>()

    private val service = DataAggregationService(
        identityPort = identityPort,
        credentialsPort = credentialsPort,
        tenancyPort = tenancyPort,
        publishingPort = publishingPort,
        mediaPort = mediaPort,
        governancePort = governancePort,
        leadCapturePort = leadCapturePort,
    )

    @Test
    fun `aggregate includes identity at top level`() = runTest {
        val principalId = "principal-1"
        val email = "user@example.com"

        coEvery { identityPort.getIdentityFacts(principalId) } returns null
        coEvery { credentialsPort.getSessions(principalId) } returns emptyList()
        coEvery { credentialsPort.getApiKeys(principalId) } returns emptyList()
        coEvery { tenancyPort.getWorkspaceMemberships(principalId) } returns emptyList()
        coEvery { publishingPort.getSocialConnections(principalId) } returns emptyList()
        coEvery { publishingPort.getSocialAccounts(principalId) } returns emptyList()
        coEvery { publishingPort.getPublications(principalId) } returns emptyList()
        coEvery { mediaPort.getMediaAssets(principalId) } returns emptyList()
        coEvery { governancePort.getConsentRecords(email) } returns emptyList()
        coEvery { leadCapturePort.getWaitlistEntries(email) } returns emptyList()

        val result = service.aggregate(principalId, email)

        assert(result.containsKey("_metadata"))
        assert(result.containsKey("identity"))
        assert(result.containsKey("workspaces"))
        assert(result.containsKey("credentials"))
        assert(result.containsKey("publishing"))
        assert(result.containsKey("media"))
        assert(result.containsKey("governance"))
        assert(result.containsKey("leadCapture"))
    }

    @Test
    fun `aggregate calls all data ports`() = runTest {
        val principalId = "principal-1"
        val email = "user@example.com"

        coEvery { identityPort.getIdentityFacts(principalId) } returns null
        coEvery { credentialsPort.getSessions(principalId) } returns emptyList()
        coEvery { credentialsPort.getApiKeys(principalId) } returns emptyList()
        coEvery { tenancyPort.getWorkspaceMemberships(principalId) } returns emptyList()
        coEvery { publishingPort.getSocialConnections(principalId) } returns emptyList()
        coEvery { publishingPort.getSocialAccounts(principalId) } returns emptyList()
        coEvery { publishingPort.getPublications(principalId) } returns emptyList()
        coEvery { mediaPort.getMediaAssets(principalId) } returns emptyList()
        coEvery { governancePort.getConsentRecords(email) } returns emptyList()
        coEvery { leadCapturePort.getWaitlistEntries(email) } returns emptyList()

        service.aggregate(principalId, email)

        coVerify { identityPort.getIdentityFacts(principalId) }
        coVerify { credentialsPort.getSessions(principalId) }
        coVerify { credentialsPort.getApiKeys(principalId) }
        coVerify { tenancyPort.getWorkspaceMemberships(principalId) }
        coVerify { publishingPort.getSocialConnections(principalId) }
        coVerify { publishingPort.getSocialAccounts(principalId) }
        coVerify { publishingPort.getPublications(principalId) }
        coVerify { mediaPort.getMediaAssets(principalId) }
        coVerify { governancePort.getConsentRecords(email) }
        coVerify { leadCapturePort.getWaitlistEntries(email) }
    }

    @Test
    fun `aggregate includes principalId and timestamp in metadata`() = runTest {
        val principalId = "principal-1"
        val email = "user@example.com"

        coEvery { identityPort.getIdentityFacts(principalId) } returns null
        coEvery { credentialsPort.getSessions(principalId) } returns emptyList()
        coEvery { credentialsPort.getApiKeys(principalId) } returns emptyList()
        coEvery { tenancyPort.getWorkspaceMemberships(principalId) } returns emptyList()
        coEvery { publishingPort.getSocialConnections(principalId) } returns emptyList()
        coEvery { publishingPort.getSocialAccounts(principalId) } returns emptyList()
        coEvery { publishingPort.getPublications(principalId) } returns emptyList()
        coEvery { mediaPort.getMediaAssets(principalId) } returns emptyList()
        coEvery { governancePort.getConsentRecords(email) } returns emptyList()
        coEvery { leadCapturePort.getWaitlistEntries(email) } returns emptyList()

        val result = service.aggregate(principalId, email)

        @Suppress("UNCHECKED_CAST")
        val metadata = result["_metadata"] as? Map<String, Any?>
        assert(metadata != null)
        assert(metadata!!["principalId"] == principalId)
        assert(metadata.containsKey("generatedAt"))
    }

    @Test
    fun `aggregate includes identity section even when facts are null`() = runTest {
        val principalId = "principal-1"
        val email = "user@example.com"

        coEvery { identityPort.getIdentityFacts(principalId) } returns null
        coEvery { credentialsPort.getSessions(principalId) } returns emptyList()
        coEvery { credentialsPort.getApiKeys(principalId) } returns emptyList()
        coEvery { tenancyPort.getWorkspaceMemberships(principalId) } returns emptyList()
        coEvery { publishingPort.getSocialConnections(principalId) } returns emptyList()
        coEvery { publishingPort.getSocialAccounts(principalId) } returns emptyList()
        coEvery { publishingPort.getPublications(principalId) } returns emptyList()
        coEvery { mediaPort.getMediaAssets(principalId) } returns emptyList()
        coEvery { governancePort.getConsentRecords(email) } returns emptyList()
        coEvery { leadCapturePort.getWaitlistEntries(email) } returns emptyList()

        val result = service.aggregate(principalId, email)

        assert(result.containsKey("identity"))
        assert(result["identity"] == null)
    }

    @Test
    fun `aggregate returns empty structures when no data exists`() = runTest {
        val principalId = "principal-1"
        val email = "user@example.com"

        coEvery { identityPort.getIdentityFacts(principalId) } returns null
        coEvery { credentialsPort.getSessions(principalId) } returns emptyList()
        coEvery { credentialsPort.getApiKeys(principalId) } returns emptyList()
        coEvery { tenancyPort.getWorkspaceMemberships(principalId) } returns emptyList()
        coEvery { publishingPort.getSocialConnections(principalId) } returns emptyList()
        coEvery { publishingPort.getSocialAccounts(principalId) } returns emptyList()
        coEvery { publishingPort.getPublications(principalId) } returns emptyList()
        coEvery { mediaPort.getMediaAssets(principalId) } returns emptyList()
        coEvery { governancePort.getConsentRecords(email) } returns emptyList()
        coEvery { leadCapturePort.getWaitlistEntries(email) } returns emptyList()

        val result = service.aggregate(principalId, email)

        @Suppress("UNCHECKED_CAST")
        val credentials = result["credentials"] as? Map<String, Any?>
        assert(credentials != null)
        assert(credentials!!["sessions"] == emptyList<Any>())
        assert(credentials["apiKeys"] == emptyList<Any>())

        @Suppress("UNCHECKED_CAST")
        val workspaces = result["workspaces"] as? List<*>
        assert(workspaces != null)
        assert(workspaces!!.isEmpty())
    }
}
