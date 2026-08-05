package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.publishing.domain.ActorRoleState
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.publishing.domain.SocialConnection
import com.profiletailors.smp.publishing.domain.SocialConnectionRepository
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialContentActor
import com.profiletailors.smp.publishing.domain.SocialContentApprovalEvidence
import com.profiletailors.smp.publishing.domain.SocialContentApprovalEvidenceRepository
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.domain.WorkspaceScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LinkedInOrganizationPageDiscoveryFacadeTest {
    @Test
    fun `missing approval blocks discovery before the handler is called`() = runTest {
        val fixture = fixture(approval = false)

        val exception = assertThrows<LinkedInOrganizationPageDiscoveryException> {
            fixture.facade.discover(fixture.account.id)
        }

        assertEquals(LinkedInOrganizationPageDiscoveryFailure.MISSING_APPROVAL, exception.failure)
        assertEquals(0, fixture.discoveryHandler.calls)
    }

    @Test
    fun `personal profile account blocks discovery`() = runTest {
        val fixture = fixture(account = fixtureAccount(kind = SocialAccountKind.PERSONAL_PROFILE))

        val exception = assertThrows<LinkedInOrganizationPageDiscoveryException> {
            fixture.facade.discover(fixture.account.id)
        }

        assertEquals(LinkedInOrganizationPageDiscoveryFailure.PERSONAL_PROFILE_ACCOUNT, exception.failure)
        assertEquals(0, fixture.discoveryHandler.calls)
    }

    @Test
    fun `active approved organization page delegates discovery`() = runTest {
        val fixture = fixture()

        val result = fixture.facade.discover(fixture.account.id)

        assertEquals(listOf(fixture.expected), result)
        assertEquals(
            listOf(DiscoveryCall(fixture.scope, fixture.connection.id, SocialProvider.LINKEDIN)),
            fixture.discoveryHandler.recordedCalls,
        )
    }

    private fun fixture(account: SocialAccount = fixtureAccount(), approval: Boolean = true): Fixture {
        val scope = WorkspaceScope("workspace-1")
        val connection = SocialConnection(
            id = "connection-1",
            workspaceId = scope.value,
            provider = SocialProvider.LINKEDIN,
            providerConnectionRef = "linkedin-connection-1",
            status = SocialConnectionStatus.ACTIVE,
        )
        val expected = SocialContentActor(
            id = "page-1",
            scope = scope,
            connectionId = connection.id,
            provider = SocialProvider.LINKEDIN,
            externalActorId = com.profiletailors.smp.publishing.domain.ProviderActorId("urn:li:organization:1"),
            kind = SocialAccountKind.ORGANIZATION_PAGE,
            displayName = "Profile Tailors Page",
            roleState = ActorRoleState.ADMIN,
            grantedScopes = setOf("rw_organization_admin"),
        )
        val discoveryHandler = RecordingDiscoveryHandler(expected)
        val facade = LinkedInOrganizationPageDiscoveryFacade(
            resourceContextProvider = FixedResourceContextProvider(scope.value),
            connectionRepository = InMemoryConnectionRepository(connection),
            accountRepository = InMemoryAccountRepository(account),
            approvalEvidenceRepository = InMemoryApprovalEvidenceRepository(
                SocialContentApprovalEvidence(
                    workspaceId = scope.value,
                    socialAccountId = account.id,
                    roleState = ActorRoleState.ADMIN,
                    grantedScopes = setOf(
                        "r_organization_social",
                        "r_organization_social_feed",
                        "rw_organization_admin",
                    ),
                    communityManagementApproved = approval,
                    apiVersion = "202601",
                    retentionPolicyVersion = "social-content-48h-24h-v1",
                ),
            ),
            discoveryHandler = discoveryHandler,
        )
        return Fixture(scope, connection, account, expected, facade, discoveryHandler)
    }

    private fun fixtureAccount(kind: SocialAccountKind = SocialAccountKind.ORGANIZATION_PAGE) = SocialAccount(
        id = "account-1",
        socialConnectionId = "connection-1",
        workspaceId = "workspace-1",
        provider = SocialProvider.LINKEDIN,
        providerAccountId = "organization-1",
        kind = kind,
        displayName = "Profile Tailors",
        status = SocialConnectionStatus.ACTIVE,
    )

    private data class Fixture(
        val scope: WorkspaceScope,
        val connection: SocialConnection,
        val account: SocialAccount,
        val expected: SocialContentActor,
        val facade: LinkedInOrganizationPageDiscoveryFacade,
        val discoveryHandler: RecordingDiscoveryHandler,
    )

    private data class DiscoveryCall(val scope: WorkspaceScope, val connectionId: String, val provider: SocialProvider)

    private class FixedResourceContextProvider(private val workspaceId: String) : ResourceContextProvider {
        override fun current(): ResourceContext = ResourceContext(ResourceContextType.WORKSPACE, workspaceId)
    }

    private class InMemoryConnectionRepository(private val connection: SocialConnection) : SocialConnectionRepository {
        override suspend fun upsert(connection: SocialConnection): SocialConnection = connection
        override suspend fun findByWorkspaceAndId(workspaceId: String, connectionId: String): SocialConnection? =
            connection.takeIf { it.workspaceId == workspaceId && it.id == connectionId }
    }

    private class InMemoryAccountRepository(private val account: SocialAccount) : SocialAccountRepository {
        override suspend fun upsert(account: SocialAccount): SocialAccount = account
        override suspend fun findByWorkspaceAndId(workspaceId: String, accountId: String): SocialAccount? =
            account.takeIf { it.workspaceId == workspaceId && it.id == accountId }
    }

    private class InMemoryApprovalEvidenceRepository(private val evidence: SocialContentApprovalEvidence) :
        SocialContentApprovalEvidenceRepository {
        override suspend fun findByWorkspaceAndAccount(
            workspaceId: String,
            socialAccountId: String,
        ): SocialContentApprovalEvidence? = evidence.takeIf {
            it.workspaceId == workspaceId && it.socialAccountId == socialAccountId
        }
    }

    private class RecordingDiscoveryHandler(private val result: SocialContentActor) : LinkedInPageDiscoveryHandler {
        val recordedCalls = mutableListOf<DiscoveryCall>()
        val calls: Int get() = recordedCalls.size

        override suspend fun handle(
            scope: WorkspaceScope,
            connectionId: String,
            provider: SocialProvider,
        ): List<SocialContentActor> {
            recordedCalls += DiscoveryCall(scope, connectionId, provider)
            return listOf(result)
        }
    }
}
