package com.profiletailors.smp.privacy.application

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class DataAggregationServiceTest {

    private val identityAnonymization = mockk<IdentityData>()
    private val credentials = mockk<CredentialsData>()
    private val tenancyData = mockk<TenancyAggregation>()
    private val publishing = mockk<PublishingData>()
    private val media = mockk<MediaData>()
    private val governanceData = mockk<GovernanceData>()
    private val leadCaptureData = mockk<LeadCaptureData>()

    private val service = DataAggregationService(
        identityAnonymization = identityAnonymization,
        credentials = credentials,
        tenancyData = tenancyData,
        publishing = publishing,
        media = media,
        governanceData = governanceData,
        leadCaptureData = leadCaptureData,
    )

    @Test
    fun `aggregate includes identity at top level`() = runTest {
        val principalId = "principal-1"
        val email = "user@example.com"

        coEvery { identityAnonymization.getIdentityFacts(principalId) } returns null
        coEvery { credentials.getSessions(principalId) } returns emptyList()
        coEvery { credentials.getApiKeys(principalId) } returns emptyList()
        coEvery { tenancyData.getWorkspaceMemberships(principalId) } returns emptyList()
        coEvery { publishing.getSocialConnections(principalId) } returns emptyList()
        coEvery { publishing.getSocialAccounts(principalId) } returns emptyList()
        coEvery { publishing.getPublications(principalId) } returns emptyList()
        coEvery { media.getMediaAssets(principalId) } returns emptyList()
        coEvery { governanceData.getConsentRecords(email) } returns emptyList()
        coEvery { leadCaptureData.getWaitlistEntries(email) } returns emptyList()

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

        coEvery { identityAnonymization.getIdentityFacts(principalId) } returns null
        coEvery { credentials.getSessions(principalId) } returns emptyList()
        coEvery { credentials.getApiKeys(principalId) } returns emptyList()
        coEvery { tenancyData.getWorkspaceMemberships(principalId) } returns emptyList()
        coEvery { publishing.getSocialConnections(principalId) } returns emptyList()
        coEvery { publishing.getSocialAccounts(principalId) } returns emptyList()
        coEvery { publishing.getPublications(principalId) } returns emptyList()
        coEvery { media.getMediaAssets(principalId) } returns emptyList()
        coEvery { governanceData.getConsentRecords(email) } returns emptyList()
        coEvery { leadCaptureData.getWaitlistEntries(email) } returns emptyList()

        service.aggregate(principalId, email)

        coVerify { identityAnonymization.getIdentityFacts(principalId) }
        coVerify { credentials.getSessions(principalId) }
        coVerify { credentials.getApiKeys(principalId) }
        coVerify { tenancyData.getWorkspaceMemberships(principalId) }
        coVerify { publishing.getSocialConnections(principalId) }
        coVerify { publishing.getSocialAccounts(principalId) }
        coVerify { publishing.getPublications(principalId) }
        coVerify { media.getMediaAssets(principalId) }
        coVerify { governanceData.getConsentRecords(email) }
        coVerify { leadCaptureData.getWaitlistEntries(email) }
    }

    @Test
    fun `aggregate includes principalId and timestamp in metadata`() = runTest {
        val principalId = "principal-1"
        val email = "user@example.com"

        coEvery { identityAnonymization.getIdentityFacts(principalId) } returns null
        coEvery { credentials.getSessions(principalId) } returns emptyList()
        coEvery { credentials.getApiKeys(principalId) } returns emptyList()
        coEvery { tenancyData.getWorkspaceMemberships(principalId) } returns emptyList()
        coEvery { publishing.getSocialConnections(principalId) } returns emptyList()
        coEvery { publishing.getSocialAccounts(principalId) } returns emptyList()
        coEvery { publishing.getPublications(principalId) } returns emptyList()
        coEvery { media.getMediaAssets(principalId) } returns emptyList()
        coEvery { governanceData.getConsentRecords(email) } returns emptyList()
        coEvery { leadCaptureData.getWaitlistEntries(email) } returns emptyList()

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

        coEvery { identityAnonymization.getIdentityFacts(principalId) } returns null
        coEvery { credentials.getSessions(principalId) } returns emptyList()
        coEvery { credentials.getApiKeys(principalId) } returns emptyList()
        coEvery { tenancyData.getWorkspaceMemberships(principalId) } returns emptyList()
        coEvery { publishing.getSocialConnections(principalId) } returns emptyList()
        coEvery { publishing.getSocialAccounts(principalId) } returns emptyList()
        coEvery { publishing.getPublications(principalId) } returns emptyList()
        coEvery { media.getMediaAssets(principalId) } returns emptyList()
        coEvery { governanceData.getConsentRecords(email) } returns emptyList()
        coEvery { leadCaptureData.getWaitlistEntries(email) } returns emptyList()

        val result = service.aggregate(principalId, email)

        assert(result.containsKey("identity"))
        assert(result["identity"] == null)
    }

    @Test
    fun `aggregate returns empty structures when no data exists`() = runTest {
        val principalId = "principal-1"
        val email = "user@example.com"

        coEvery { identityAnonymization.getIdentityFacts(principalId) } returns null
        coEvery { credentials.getSessions(principalId) } returns emptyList()
        coEvery { credentials.getApiKeys(principalId) } returns emptyList()
        coEvery { tenancyData.getWorkspaceMemberships(principalId) } returns emptyList()
        coEvery { publishing.getSocialConnections(principalId) } returns emptyList()
        coEvery { publishing.getSocialAccounts(principalId) } returns emptyList()
        coEvery { publishing.getPublications(principalId) } returns emptyList()
        coEvery { media.getMediaAssets(principalId) } returns emptyList()
        coEvery { governanceData.getConsentRecords(email) } returns emptyList()
        coEvery { leadCaptureData.getWaitlistEntries(email) } returns emptyList()

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
