package com.profiletailors.smp.identity.application

import com.profiletailors.smp.privacy.application.CredentialsRevocationPort
import com.profiletailors.smp.privacy.application.IdentityAnonymizationPort
import com.profiletailors.smp.privacy.application.MediaDeletionPort
import com.profiletailors.smp.privacy.application.PublishingDeletionPort
import com.profiletailors.smp.privacy.application.TenancyDataPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CloseAccountHandlerTest {

    private val fixedClock = Clock.fixed(Instant.parse("2026-07-22T10:00:00Z"), ZoneOffset.UTC)

    private val identityAnonymizationPort: IdentityAnonymizationPort = mockk()
    private val credentialsRevocationPort: CredentialsRevocationPort = mockk()
    private val publishingDeletionPort: PublishingDeletionPort = mockk()
    private val mediaDeletionPort: MediaDeletionPort = mockk()
    private val tenancyDataPort: TenancyDataPort = mockk()

    private val handler = CloseAccountHandler(
        identityAnonymizationPort = identityAnonymizationPort,
        credentialsRevocationPort = credentialsRevocationPort,
        publishingDeletionPort = publishingDeletionPort,
        mediaDeletionPort = mediaDeletionPort,
        tenancyDataPort = tenancyDataPort,
        clock = fixedClock,
    )

    @Nested
    inner class Validation {

        @Test
        fun `throws when confirmation is not DELETE`() = runTest {
            val result = kotlin.runCatching {
                handler.handle(
                    CloseAccountCommand(
                        principalId = "principal-1",
                        confirmation = "WRONG",
                    ),
                )
            }
            assert(result.isFailure)
            val exception = result.exceptionOrNull()
            assert(exception is IllegalArgumentException)
            assert(exception!!.message!!.contains("DELETE"))
        }

        @Test
        fun `accepts valid confirmation and invokes all ports in order`() = runTest {
            coEvery { tenancyDataPort.getMembershipWorkspaceIds(any()) } returns listOf("workspace-1")
            coEvery { credentialsRevocationPort.revokeAllSessions(any()) } returns Unit
            coEvery { credentialsRevocationPort.deleteAllApiKeys(any()) } returns Unit
            coEvery { publishingDeletionPort.cancelPendingPublications(any()) } returns Unit
            coEvery { publishingDeletionPort.deleteSocialConnections(any()) } returns Unit
            coEvery { publishingDeletionPort.deleteSecureCredentials(any()) } returns Unit
            coEvery { mediaDeletionPort.markAssetsDeleted(any(), any()) } returns Unit
            coEvery { mediaDeletionPort.markBlobsReadyForGc(any(), any()) } returns Unit
            coEvery { tenancyDataPort.removeAllMemberships(any()) } returns listOf("workspace-1")
            coEvery { identityAnonymizationPort.anonymizeUserIdentity(any(), any()) } returns Unit
            coEvery { identityAnonymizationPort.anonymizePrincipalDisplayIdentity(any()) } returns Unit

            handler.handle(
                CloseAccountCommand(
                    principalId = "principal-1",
                    confirmation = "DELETE",
                ),
            )

            coVerifyOrder {
                credentialsRevocationPort.revokeAllSessions("principal-1")
                credentialsRevocationPort.deleteAllApiKeys("principal-1")
                publishingDeletionPort.cancelPendingPublications("principal-1")
                publishingDeletionPort.deleteSocialConnections("principal-1")
                publishingDeletionPort.deleteSecureCredentials("principal-1")
                tenancyDataPort.getMembershipWorkspaceIds("principal-1")
                mediaDeletionPort.markAssetsDeleted("principal-1", listOf("workspace-1"))
                mediaDeletionPort.markBlobsReadyForGc("principal-1", listOf("workspace-1"))
                tenancyDataPort.removeAllMemberships("principal-1")
                identityAnonymizationPort.anonymizeUserIdentity("principal-1", Instant.parse("2026-07-22T10:00:00Z"))
                identityAnonymizationPort.anonymizePrincipalDisplayIdentity("principal-1")
            }
        }

        @Test
        fun `does not call media ports when no workspace memberships exist`() = runTest {
            coEvery { tenancyDataPort.getMembershipWorkspaceIds(any()) } returns emptyList()
            coEvery { credentialsRevocationPort.revokeAllSessions(any()) } returns Unit
            coEvery { credentialsRevocationPort.deleteAllApiKeys(any()) } returns Unit
            coEvery { publishingDeletionPort.cancelPendingPublications(any()) } returns Unit
            coEvery { publishingDeletionPort.deleteSocialConnections(any()) } returns Unit
            coEvery { publishingDeletionPort.deleteSecureCredentials(any()) } returns Unit
            coEvery { tenancyDataPort.removeAllMemberships(any()) } returns emptyList()
            coEvery { identityAnonymizationPort.anonymizeUserIdentity(any(), any()) } returns Unit
            coEvery { identityAnonymizationPort.anonymizePrincipalDisplayIdentity(any()) } returns Unit

            handler.handle(
                CloseAccountCommand(
                    principalId = "principal-1",
                    confirmation = "DELETE",
                ),
            )

            coVerify(exactly = 0) { mediaDeletionPort.markAssetsDeleted(any(), any()) }
            coVerify(exactly = 0) { mediaDeletionPort.markBlobsReadyForGc(any(), any()) }
        }
    }

    @Nested
    inner class RateLimiting {

        @Test
        fun `throws rate limit exception on second attempt within 5 minutes`() = runTest {
            coEvery { tenancyDataPort.getMembershipWorkspaceIds(any()) } returns emptyList()
            coEvery { credentialsRevocationPort.revokeAllSessions(any()) } returns Unit
            coEvery { credentialsRevocationPort.deleteAllApiKeys(any()) } returns Unit
            coEvery { publishingDeletionPort.cancelPendingPublications(any()) } returns Unit
            coEvery { publishingDeletionPort.deleteSocialConnections(any()) } returns Unit
            coEvery { publishingDeletionPort.deleteSecureCredentials(any()) } returns Unit
            coEvery { tenancyDataPort.removeAllMemberships(any()) } returns emptyList()
            coEvery { identityAnonymizationPort.anonymizeUserIdentity(any(), any()) } returns Unit
            coEvery { identityAnonymizationPort.anonymizePrincipalDisplayIdentity(any()) } returns Unit
            coEvery { mediaDeletionPort.markAssetsDeleted(any(), any()) } returns Unit
            coEvery { mediaDeletionPort.markBlobsReadyForGc(any(), any()) } returns Unit

            // First attempt succeeds
            handler.handle(
                CloseAccountCommand(principalId = "rate-limited-user", confirmation = "DELETE"),
            )

            // Second attempt within 5 minutes fails
            val result = kotlin.runCatching {
                handler.handle(
                    CloseAccountCommand(principalId = "rate-limited-user", confirmation = "DELETE"),
                )
            }
            assert(result.isFailure)
            val exception = result.exceptionOrNull()
            assert(exception is CloseAccountRateLimitException)
            assert(exception!!.message!!.contains("rate limit", ignoreCase = true))
        }
    }
}
