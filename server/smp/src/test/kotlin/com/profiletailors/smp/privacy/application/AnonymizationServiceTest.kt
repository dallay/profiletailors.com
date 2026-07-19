package com.profiletailors.smp.privacy.application

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

class AnonymizationServiceTest {

    private val identityPort = mockk<IdentityAnonymizationPort>(relaxed = true)
    private val waitlistPort = mockk<WaitlistAnonymizationPort>(relaxed = true)
    private val credentialsPort = mockk<CredentialsRevocationPort>(relaxed = true)
    private val tenancyPort = mockk<TenancyDataPort>(relaxed = true)
    private val publishingPort = mockk<PublishingDeletionPort>(relaxed = true)
    private val mediaPort = mockk<MediaDeletionPort>(relaxed = true)

    private val service = AnonymizationService(
        identityPort = identityPort,
        waitlistPort = waitlistPort,
        credentialsPort = credentialsPort,
        tenancyPort = tenancyPort,
        publishingPort = publishingPort,
        mediaPort = mediaPort,
    )

    // ——————— anonymizePII ———————

    @Test
    fun `anonymizePII anonymizes user identity and principals`() = runTest {
        val principalId = "principal-1"
        val email = "user@example.com"
        val timestamp = Instant.parse("2026-07-19T10:00:00Z")

        service.anonymizePII(principalId, email, timestamp)

        coVerify { identityPort.anonymizeUserIdentity(principalId, timestamp) }
        coVerify { identityPort.anonymizePrincipalDisplayIdentity(principalId) }
    }

    @Test
    fun `anonymizePII anonymizes waitlist entries`() = runTest {
        val principalId = "principal-1"
        val email = "user@example.com"
        val timestamp = Instant.parse("2026-07-19T10:00:00Z")

        service.anonymizePII(principalId, email, timestamp)

        coVerify { waitlistPort.anonymizeByEmail(email, timestamp) }
    }

    @Test
    fun `anonymizePII revokes all sessions and API keys`() = runTest {
        val principalId = "principal-1"
        val email = "user@example.com"
        val timestamp = Instant.parse("2026-07-19T10:00:00Z")

        service.anonymizePII(principalId, email, timestamp)

        coVerify { credentialsPort.revokeAllSessions(principalId) }
        coVerify { credentialsPort.deleteAllApiKeys(principalId) }
    }

    @Test
    fun `anonymizePII delegates to identity port in expected order`() = runTest {
        val principalId = "principal-1"
        val email = "user@example.com"
        val timestamp = Instant.parse("2026-07-19T10:00:00Z")

        service.anonymizePII(principalId, email, timestamp)

        coVerifySequence {
            identityPort.anonymizeUserIdentity(principalId, timestamp)
            identityPort.anonymizePrincipalDisplayIdentity(principalId)
            waitlistPort.anonymizeByEmail(email, timestamp)
            credentialsPort.revokeAllSessions(principalId)
            credentialsPort.deleteAllApiKeys(principalId)
        }
    }

    // ——————— verifyCorrection ———————

    @Test
    fun `verifyCorrection returns success when email is available`() = runTest {
        val email = "new@example.com"
        coEvery { identityPort.correctUserIdentityEmail(any(), any()) } returns "old@example.com"

        val result = service.verifyCorrection("principal-1", CorrectionField.EMAIL, email)

        assert(result is AnonymizationService.CorrectionResult.Success)
    }

    @Test
    fun `verifyCorrection returns not found when principal does not exist`() = runTest {
        val email = "new@example.com"
        coEvery { identityPort.correctUserIdentityEmail(any(), any()) } returns null

        val result = service.verifyCorrection("principal-1", CorrectionField.EMAIL, email)

        assert(result is AnonymizationService.CorrectionResult.NotFound)
    }

    @Test
    fun `verifyCorrection delegates to correctUserIdentityEmail with EMAIL field`() = runTest {
        val principalId = "principal-1"
        val newValue = "new@example.com"

        coEvery { identityPort.correctUserIdentityEmail(any(), any()) } returns "old@example.com"
        service.verifyCorrection(principalId, CorrectionField.EMAIL, newValue)

        coVerify { identityPort.correctUserIdentityEmail(principalId, newValue) }
    }

    @Test
    fun `verifyCorrection delegates to correctUserIdentityUsername with USERNAME field`() = runTest {
        val principalId = "principal-1"
        val newValue = "newuser"

        coEvery { identityPort.correctUserIdentityUsername(any(), any()) } returns "olduser"
        service.verifyCorrection(principalId, CorrectionField.USERNAME, newValue)

        coVerify { identityPort.correctUserIdentityUsername(principalId, newValue) }
    }

    // ——————— deleteData ———————

    @Test
    fun `deleteData removes all memberships and collects workspace IDs`() = runTest {
        val principalId = "principal-1"
        coEvery { tenancyPort.removeAllMemberships(principalId) } returns listOf("ws-1", "ws-2")
        coEvery { publishingPort.deleteSocialConnections(principalId) } returns Unit
        coEvery { publishingPort.deleteSecureCredentials(principalId) } returns Unit
        coEvery { publishingPort.cancelPendingPublications(principalId) } returns Unit
        coEvery { mediaPort.markAssetsDeleted(principalId, listOf("ws-1", "ws-2")) } returns Unit
        coEvery { mediaPort.markBlobsReadyForGc(principalId, listOf("ws-1", "ws-2")) } returns Unit

        service.deleteData(principalId)

        coVerify { tenancyPort.removeAllMemberships(principalId) }
    }

    @Test
    fun `deleteData passes workspace IDs to media deletion`() = runTest {
        val principalId = "principal-1"
        coEvery { tenancyPort.removeAllMemberships(principalId) } returns listOf("ws-1", "ws-2")

        service.deleteData(principalId)

        coVerify { mediaPort.markAssetsDeleted(principalId, listOf("ws-1", "ws-2")) }
        coVerify { mediaPort.markBlobsReadyForGc(principalId, listOf("ws-1", "ws-2")) }
    }

    @Test
    fun `deleteData revokes credentials and deletes publishing data`() = runTest {
        val principalId = "principal-1"
        coEvery { tenancyPort.removeAllMemberships(principalId) } returns emptyList()

        service.deleteData(principalId)

        coVerify { publishingPort.deleteSocialConnections(principalId) }
        coVerify { publishingPort.deleteSecureCredentials(principalId) }
        coVerify { publishingPort.cancelPendingPublications(principalId) }
        coVerify { credentialsPort.revokeAllSessions(principalId) }
        coVerify { credentialsPort.deleteAllApiKeys(principalId) }
    }

    @Test
    fun `deleteData chains operations in expected order`() = runTest {
        val principalId = "principal-1"
        coEvery { tenancyPort.removeAllMemberships(principalId) } returns listOf("ws-1")

        service.deleteData(principalId)

        coVerifySequence {
            tenancyPort.removeAllMemberships(principalId)
            mediaPort.markAssetsDeleted(principalId, listOf("ws-1"))
            mediaPort.markBlobsReadyForGc(principalId, listOf("ws-1"))
            publishingPort.deleteSocialConnections(principalId)
            publishingPort.deleteSecureCredentials(principalId)
            publishingPort.cancelPendingPublications(principalId)
            credentialsPort.revokeAllSessions(principalId)
            credentialsPort.deleteAllApiKeys(principalId)
        }
    }
}
