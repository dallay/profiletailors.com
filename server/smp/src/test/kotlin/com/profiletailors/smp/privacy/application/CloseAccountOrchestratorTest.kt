package com.profiletailors.smp.privacy.application

import io.mockk.coEvery
import io.mockk.coVerifySequence
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class CloseAccountOrchestratorTest {

    private val identityAnonymization = mockk<IdentityAnonymization>(relaxed = true)
    private val credentialsRevocation = mockk<CredentialsRevocation>(relaxed = true)
    private val publishingDeletion = mockk<PublishingDeletion>(relaxed = true)
    private val mediaDeletion = mockk<MediaDeletion>(relaxed = true)
    private val tenancyData = mockk<TenancyData>(relaxed = true)

    private val orchestrator = CloseAccountOrchestrator(
        identityAnonymization = identityAnonymization,
        credentialsRevocation = credentialsRevocation,
        publishingDeletion = publishingDeletion,
        mediaDeletion = mediaDeletion,
        tenancyData = tenancyData,
    )

    @Test
    fun `execute revokes credentials before cleaning publishing`() = runTest {
        val principalId = "principal-1"
        coEvery { tenancyData.getMembershipWorkspaceIds(principalId) } returns emptyList()

        orchestrator.execute(principalId)

        coVerifySequence {
            credentialsRevocation.revokeAllSessions(principalId)
            credentialsRevocation.deleteAllApiKeys(principalId)
            publishingDeletion.cancelPendingPublications(principalId)
            publishingDeletion.deleteSocialConnections(principalId)
            publishingDeletion.deleteSecureCredentials(principalId)
            tenancyData.getMembershipWorkspaceIds(principalId)
            tenancyData.removeAllMemberships(principalId)
            identityAnonymization.anonymizeUserIdentity(principalId, any())
            identityAnonymization.anonymizePrincipalDisplayIdentity(principalId)
        }
    }

    @Test
    fun `execute marks media for deletion when principal has workspaces`() = runTest {
        val principalId = "principal-1"
        val workspaceIds = listOf("ws-1", "ws-2")
        coEvery { tenancyData.getMembershipWorkspaceIds(principalId) } returns workspaceIds

        orchestrator.execute(principalId)

        coVerifySequence {
            credentialsRevocation.revokeAllSessions(principalId)
            credentialsRevocation.deleteAllApiKeys(principalId)
            publishingDeletion.cancelPendingPublications(principalId)
            publishingDeletion.deleteSocialConnections(principalId)
            publishingDeletion.deleteSecureCredentials(principalId)
            tenancyData.getMembershipWorkspaceIds(principalId)
            mediaDeletion.markAssetsDeleted(principalId, workspaceIds)
            mediaDeletion.markBlobsReadyForGc(principalId, workspaceIds)
            tenancyData.removeAllMemberships(principalId)
            identityAnonymization.anonymizeUserIdentity(principalId, any())
            identityAnonymization.anonymizePrincipalDisplayIdentity(principalId)
        }
    }

    @Test
    fun `execute skips media deletion when principal has no workspaces`() = runTest {
        val principalId = "principal-1"
        coEvery { tenancyData.getMembershipWorkspaceIds(principalId) } returns emptyList()

        orchestrator.execute(principalId)

        io.mockk.coVerify(exactly = 0) { mediaDeletion.markAssetsDeleted(any(), any()) }
        io.mockk.coVerify(exactly = 0) { mediaDeletion.markBlobsReadyForGc(any(), any()) }
    }

    @Test
    fun `execute removes memberships before anonymizing identity`() = runTest {
        val principalId = "principal-1"
        coEvery { tenancyData.getMembershipWorkspaceIds(principalId) } returns emptyList()

        orchestrator.execute(principalId)

        coVerifySequence {
            tenancyData.getMembershipWorkspaceIds(principalId)
            tenancyData.removeAllMemberships(principalId)
            identityAnonymization.anonymizeUserIdentity(principalId, any())
            identityAnonymization.anonymizePrincipalDisplayIdentity(principalId)
        }
    }
}
