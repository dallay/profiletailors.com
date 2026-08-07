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

    @Test
    fun `account and connection gates deny discovery before approval lookup`() = runTest {
        val cases = listOf(
            fixture(account = fixtureAccount(status = SocialConnectionStatus.REVOKED)) to
                LinkedInOrganizationPageDiscoveryFailure.INACTIVE_ACCOUNT,
            fixture(connectionStatus = SocialConnectionStatus.REVOKED) to
                LinkedInOrganizationPageDiscoveryFailure.INACTIVE_CONNECTION,
        )

        cases.forEach { (fixture, expectedFailure) ->
            val exception = assertThrows<LinkedInOrganizationPageDiscoveryException> {
                fixture.facade.discover(fixture.account.id)
            }

            assertEquals(expectedFailure, exception.failure)
            assertEquals(0, fixture.discoveryHandler.calls)
        }
    }

    @Test
    fun `missing account and connection are reported before discovery`() = runTest {
        val missingAccount = fixture()
        val accountException = assertThrows<LinkedInOrganizationPageDiscoveryException> {
            missingAccount.facade.discover("missing-account")
        }
        assertEquals(LinkedInOrganizationPageDiscoveryFailure.ACCOUNT_NOT_FOUND, accountException.failure)

        val missingConnection = fixture(connectionId = "missing-connection")
        val connectionException = assertThrows<LinkedInOrganizationPageDiscoveryException> {
            missingConnection.facade.discover(missingConnection.account.id)
        }
        assertEquals(LinkedInOrganizationPageDiscoveryFailure.CONNECTION_NOT_FOUND, connectionException.failure)
    }

    @Test
    fun `approval evidence validation reports the first unmet requirement`() = runTest {
        val base = evidence()
        val cases = listOf(
            base.copy(workspaceId = "other-workspace") to
                LinkedInOrganizationPageDiscoveryFailure.INVALID_APPROVAL_EVIDENCE,
            base.copy(communityManagementApproved = false) to
                LinkedInOrganizationPageDiscoveryFailure.MISSING_APPROVAL,
            base.copy(roleState = ActorRoleState.MEMBER) to
                LinkedInOrganizationPageDiscoveryFailure.ADMIN_ROLE_REQUIRED,
            base.copy(grantedScopes = setOf("r_organization_social")) to
                LinkedInOrganizationPageDiscoveryFailure.REQUIRED_SCOPE_MISSING,
            base.copy(apiVersion = "") to
                LinkedInOrganizationPageDiscoveryFailure.API_VERSION_MISSING,
            base.copy(retentionPolicyVersion = "") to
                LinkedInOrganizationPageDiscoveryFailure.RETENTION_POLICY_VERSION_MISSING,
        )

        cases.forEach { (approvalEvidence, expectedFailure) ->
            val fixture = fixture(approvalEvidence = approvalEvidence)
            val exception = assertThrows<LinkedInOrganizationPageDiscoveryException> {
                fixture.facade.discover(fixture.account.id)
            }
            assertEquals(expectedFailure, exception.failure)
            assertEquals(0, fixture.discoveryHandler.calls)
        }
    }

    private fun fixture(
        account: SocialAccount = fixtureAccount(),
        approval: Boolean = true,
        connectionId: String = "connection-1",
        connectionStatus: SocialConnectionStatus = SocialConnectionStatus.ACTIVE,
        approvalEvidence: SocialContentApprovalEvidence? = null,
    ): Fixture {
        val scope = WorkspaceScope("workspace-1")
        val connection = SocialConnection(
            id = connectionId,
            workspaceId = scope.value,
            provider = SocialProvider.LINKEDIN,
            providerConnectionRef = "linkedin-connection-1",
            status = connectionStatus,
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
                approvalEvidence ?: evidence(
                    workspaceId = scope.value,
                    socialAccountId = account.id,
                    communityManagementApproved = approval,
                ),
                allowMismatchedEvidence = approvalEvidence != null,
            ),
            discoveryHandler = discoveryHandler,
        )
        return Fixture(scope, connection, account, expected, facade, discoveryHandler)
    }

    private fun evidence(
        workspaceId: String = "workspace-1",
        socialAccountId: String = "account-1",
        communityManagementApproved: Boolean = true,
        roleState: ActorRoleState = ActorRoleState.ADMIN,
        grantedScopes: Set<String> = setOf("r_organization_social", "r_organization_social_feed"),
        apiVersion: String = "202601",
        retentionPolicyVersion: String = "social-content-48h-24h-v1",
    ) = SocialContentApprovalEvidence(
        workspaceId = workspaceId,
        socialAccountId = socialAccountId,
        roleState = roleState,
        grantedScopes = grantedScopes,
        communityManagementApproved = communityManagementApproved,
        apiVersion = apiVersion,
        retentionPolicyVersion = retentionPolicyVersion,
    )

    private fun fixtureAccount(
        kind: SocialAccountKind = SocialAccountKind.ORGANIZATION_PAGE,
        provider: SocialProvider = SocialProvider.LINKEDIN,
        status: SocialConnectionStatus = SocialConnectionStatus.ACTIVE,
    ) = SocialAccount(
        id = "account-1",
        socialConnectionId = "connection-1",
        workspaceId = "workspace-1",
        provider = provider,
        providerAccountId = "organization-1",
        kind = kind,
        displayName = "Profile Tailors",
        status = status,
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

    private class InMemoryApprovalEvidenceRepository(
        private val evidence: SocialContentApprovalEvidence,
        private val allowMismatchedEvidence: Boolean = false,
    ) : SocialContentApprovalEvidenceRepository {
        override suspend fun findByWorkspaceAndAccount(
            workspaceId: String,
            socialAccountId: String,
        ): SocialContentApprovalEvidence? = evidence.takeIf {
            allowMismatchedEvidence || it.workspaceId == workspaceId && it.socialAccountId == socialAccountId
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
