package com.profiletailors.smp.privacy.application

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

class AnonymizationServiceTest {

    private val identityAnonymization = mockk<IdentityAnonymization>(relaxed = true)
    private val waitlistAnonymization = mockk<WaitlistAnonymization>(relaxed = true)
    private val credentials = mockk<CredentialsRevocation>(relaxed = true)
    private val tenancyData = mockk<TenancyData>(relaxed = true)
    private val publishing = mockk<PublishingDeletion>(relaxed = true)
    private val media = mockk<MediaDeletion>(relaxed = true)

    private val service = AnonymizationService(
        identityAnonymization = identityAnonymization,
        waitlistAnonymization = waitlistAnonymization,
        credentials = credentials,
        tenancyData = tenancyData,
        publishing = publishing,
        media = media,
    )

    // ——————— anonymizePII ———————

    @Test
    fun `anonymizePII anonymizes user identity and principals`() = runTest {
        val principalId = "principal-1"
        val email = "user@example.com"
        val timestamp = Instant.parse("2026-07-19T10:00:00Z")

        service.anonymizePII(principalId, email, timestamp)

        coVerify { identityAnonymization.anonymizeUserIdentity(principalId, timestamp) }
        coVerify { identityAnonymization.anonymizePrincipalDisplayIdentity(principalId) }
    }

    @Test
    fun `anonymizePII anonymizes waitlist entries`() = runTest {
        val principalId = "principal-1"
        val email = "user@example.com"
        val timestamp = Instant.parse("2026-07-19T10:00:00Z")

        service.anonymizePII(principalId, email, timestamp)

        coVerify { waitlistAnonymization.anonymizeByEmail(email, timestamp) }
    }

    @Test
    fun `anonymizePII revokes all sessions and API keys`() = runTest {
        val principalId = "principal-1"
        val email = "user@example.com"
        val timestamp = Instant.parse("2026-07-19T10:00:00Z")

        service.anonymizePII(principalId, email, timestamp)

        coVerify { credentials.revokeAllSessions(principalId) }
        coVerify { credentials.deleteAllApiKeys(principalId) }
    }

    @Test
    fun `anonymizePII delegates to identity port in expected order`() = runTest {
        val principalId = "principal-1"
        val email = "user@example.com"
        val timestamp = Instant.parse("2026-07-19T10:00:00Z")

        service.anonymizePII(principalId, email, timestamp)

        coVerifySequence {
            identityAnonymization.anonymizeUserIdentity(principalId, timestamp)
            identityAnonymization.anonymizePrincipalDisplayIdentity(principalId)
            waitlistAnonymization.anonymizeByEmail(email, timestamp)
            credentials.revokeAllSessions(principalId)
            credentials.deleteAllApiKeys(principalId)
        }
    }

    // ——————— verifyCorrection ———————

    @Test
    fun `verifyCorrection returns success when email is available`() = runTest {
        val email = "new@example.com"
        coEvery { identityAnonymization.correctUserIdentityEmail(any(), any()) } returns "old@example.com"

        val result = service.verifyCorrection("principal-1", CorrectionField.EMAIL, email)

        assert(result is AnonymizationService.CorrectionResult.Success)
    }

    @Test
    fun `verifyCorrection returns not found when principal does not exist`() = runTest {
        val email = "new@example.com"
        coEvery { identityAnonymization.correctUserIdentityEmail(any(), any()) } returns null

        val result = service.verifyCorrection("principal-1", CorrectionField.EMAIL, email)

        assert(result is AnonymizationService.CorrectionResult.NotFound)
    }

    @Test
    fun `verifyCorrection delegates to correctUserIdentityEmail with EMAIL field`() = runTest {
        val principalId = "principal-1"
        val newValue = "new@example.com"

        coEvery { identityAnonymization.correctUserIdentityEmail(any(), any()) } returns "old@example.com"
        service.verifyCorrection(principalId, CorrectionField.EMAIL, newValue)

        coVerify { identityAnonymization.correctUserIdentityEmail(principalId, newValue) }
    }

    @Test
    fun `verifyCorrection delegates to correctUserIdentityUsername with USERNAME field`() = runTest {
        val principalId = "principal-1"
        val newValue = "newuser"

        coEvery { identityAnonymization.correctUserIdentityUsername(any(), any()) } returns "olduser"
        service.verifyCorrection(principalId, CorrectionField.USERNAME, newValue)

        coVerify { identityAnonymization.correctUserIdentityUsername(principalId, newValue) }
    }

    // ——————— deleteData ———————

    @Test
    fun `deleteData removes all memberships and collects workspace IDs`() = runTest {
        val principalId = "principal-1"
        coEvery { tenancyData.removeAllMemberships(principalId) } returns listOf("ws-1", "ws-2")
        coEvery { publishing.deleteSocialConnections(principalId) } returns Unit
        coEvery { publishing.deleteSecureCredentials(principalId) } returns Unit
        coEvery { publishing.cancelPendingPublications(principalId) } returns Unit
        coEvery { media.markAssetsDeleted(principalId, listOf("ws-1", "ws-2")) } returns Unit
        coEvery { media.markBlobsReadyForGc(principalId, listOf("ws-1", "ws-2")) } returns Unit

        service.deleteData(principalId)

        coVerify { tenancyData.removeAllMemberships(principalId) }
    }

    @Test
    fun `deleteData passes workspace IDs to media deletion`() = runTest {
        val principalId = "principal-1"
        coEvery { tenancyData.removeAllMemberships(principalId) } returns listOf("ws-1", "ws-2")

        service.deleteData(principalId)

        coVerify { media.markAssetsDeleted(principalId, listOf("ws-1", "ws-2")) }
        coVerify { media.markBlobsReadyForGc(principalId, listOf("ws-1", "ws-2")) }
    }

    @Test
    fun `deleteData revokes credentials and deletes publishing data`() = runTest {
        val principalId = "principal-1"
        coEvery { tenancyData.removeAllMemberships(principalId) } returns emptyList()

        service.deleteData(principalId)

        coVerify { publishing.deleteSocialConnections(principalId) }
        coVerify { publishing.deleteSecureCredentials(principalId) }
        coVerify { publishing.cancelPendingPublications(principalId) }
        coVerify { credentials.revokeAllSessions(principalId) }
        coVerify { credentials.deleteAllApiKeys(principalId) }
    }

    @Test
    fun `deleteData chains operations in expected order`() = runTest {
        val principalId = "principal-1"
        coEvery { tenancyData.removeAllMemberships(principalId) } returns listOf("ws-1")

        service.deleteData(principalId)

        coVerifySequence {
            tenancyData.removeAllMemberships(principalId)
            media.markAssetsDeleted(principalId, listOf("ws-1"))
            media.markBlobsReadyForGc(principalId, listOf("ws-1"))
            publishing.deleteSocialConnections(principalId)
            publishing.deleteSecureCredentials(principalId)
            publishing.cancelPendingPublications(principalId)
            credentials.revokeAllSessions(principalId)
            credentials.deleteAllApiKeys(principalId)
        }
    }
}
