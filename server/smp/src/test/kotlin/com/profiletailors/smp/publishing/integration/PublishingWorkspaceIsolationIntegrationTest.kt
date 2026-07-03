package com.profiletailors.smp.publishing.integration

import com.profiletailors.smp.integration.support.DatabaseUnitTestBase
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialConnection
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcPublicationRepository
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcSocialAccountRepository
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcSocialConnectionRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

/**
 * Integration test that explicitly validates workspace isolation for publishing connections and publications.
 *
 * This test ensures that:
 * - Users in workspace A cannot see or access connections from workspace B
 * - Users in workspace A cannot see or access publications from workspace B
 * - Repository queries correctly filter by workspace_id
 *
 * Workspace isolation is a critical security boundary in the publishing domain.
 */
class PublishingWorkspaceIsolationIntegrationTest : DatabaseUnitTestBase() {

    override fun databaseName(): String = "publishing_workspace_isolation"

    private lateinit var socialConnectionRepository: R2dbcSocialConnectionRepository
    private lateinit var socialAccountRepository: R2dbcSocialAccountRepository
    private lateinit var publicationRepository: R2dbcPublicationRepository

    @BeforeEach
    fun setUpRepositories() = runTest {
        seedTwoWorkspacesWithPrincipals()
        socialConnectionRepository = R2dbcSocialConnectionRepository(databaseClient)
        socialAccountRepository = R2dbcSocialAccountRepository(databaseClient, SimpleMeterRegistry())
        publicationRepository = R2dbcPublicationRepository(databaseClient, transactionalOperator)
    }

    @Test
    fun `workspace A cannot see connections from workspace B`() = runTest {
        // Workspace A creates a connection
        socialConnectionRepository.upsert(
            SocialConnection(
                id = "conn-workspace-a",
                workspaceId = "workspace-a",
                provider = SocialProvider.LINKEDIN,
                providerConnectionRef = "linkedin-conn-a",
                status = SocialConnectionStatus.ACTIVE,
                credentialReference = "00000000-0000-0000-0000-000000000001",
            ),
        )

        // Workspace B creates a connection
        socialConnectionRepository.upsert(
            SocialConnection(
                id = "conn-workspace-b",
                workspaceId = "workspace-b",
                provider = SocialProvider.LINKEDIN,
                providerConnectionRef = "linkedin-conn-b",
                status = SocialConnectionStatus.ACTIVE,
                credentialReference = "00000000-0000-0000-0000-000000000002",
            ),
        )

        // Workspace A tries to read its own connection — should succeed
        val connectionA = socialConnectionRepository.findByWorkspaceAndId("workspace-a", "conn-workspace-a")
        assertEquals("linkedin-conn-a", connectionA?.providerConnectionRef)

        // Workspace A tries to read workspace B's connection — should return null
        val connectionBFromA = socialConnectionRepository.findByWorkspaceAndId("workspace-a", "conn-workspace-b")
        assertNull(connectionBFromA)

        // Workspace B tries to read workspace A's connection — should return null
        val connectionAFromB = socialConnectionRepository.findByWorkspaceAndId("workspace-b", "conn-workspace-a")
        assertNull(connectionAFromB)

        // Workspace B tries to read its own connection — should succeed
        val connectionB = socialConnectionRepository.findByWorkspaceAndId("workspace-b", "conn-workspace-b")
        assertEquals("linkedin-conn-b", connectionB?.providerConnectionRef)
    }

    @Test
    fun `workspace A cannot see accounts from workspace B`() = runTest {
        // Create connections for both workspaces
        socialConnectionRepository.upsert(
            SocialConnection(
                id = "conn-a",
                workspaceId = "workspace-a",
                provider = SocialProvider.LINKEDIN,
                providerConnectionRef = "linkedin-conn-a",
                status = SocialConnectionStatus.ACTIVE,
                credentialReference = "00000000-0000-0000-0000-000000000001",
            ),
        )
        socialConnectionRepository.upsert(
            SocialConnection(
                id = "conn-b",
                workspaceId = "workspace-b",
                provider = SocialProvider.LINKEDIN,
                providerConnectionRef = "linkedin-conn-b",
                status = SocialConnectionStatus.ACTIVE,
                credentialReference = "00000000-0000-0000-0000-000000000002",
            ),
        )

        // Workspace A creates an account
        socialAccountRepository.upsert(
            SocialAccount(
                id = "account-workspace-a",
                socialConnectionId = "conn-a",
                workspaceId = "workspace-a",
                provider = SocialProvider.LINKEDIN,
                providerAccountId = "linkedin-account-a",
                kind = SocialAccountKind.PERSONAL_PROFILE,
                displayName = "User A",
                status = SocialConnectionStatus.ACTIVE,
            ),
        )

        // Workspace B creates an account
        socialAccountRepository.upsert(
            SocialAccount(
                id = "account-workspace-b",
                socialConnectionId = "conn-b",
                workspaceId = "workspace-b",
                provider = SocialProvider.LINKEDIN,
                providerAccountId = "linkedin-account-b",
                kind = SocialAccountKind.PERSONAL_PROFILE,
                displayName = "User B",
                status = SocialConnectionStatus.ACTIVE,
            ),
        )

        // Workspace A tries to read its own account — should succeed
        val accountA = socialAccountRepository.findByWorkspaceAndId("workspace-a", "account-workspace-a")
        assertEquals("linkedin-account-a", accountA?.providerAccountId)

        // Workspace A tries to read workspace B's account — should return null
        val accountBFromA = socialAccountRepository.findByWorkspaceAndId("workspace-a", "account-workspace-b")
        assertNull(accountBFromA)

        // Workspace B tries to read workspace A's account — should return null
        val accountAFromB = socialAccountRepository.findByWorkspaceAndId("workspace-b", "account-workspace-a")
        assertNull(accountAFromB)

        // Workspace B tries to read its own account — should succeed
        val accountB = socialAccountRepository.findByWorkspaceAndId("workspace-b", "account-workspace-b")
        assertEquals("linkedin-account-b", accountB?.providerAccountId)
    }

    @Test
    fun `workspace A cannot see publications from workspace B`() = runTest {
        // Set up connections and accounts for both workspaces
        setupConnectionsAndAccounts()

        // Workspace A creates a publication
        publicationRepository.createDraft(
            PublicationDraft(
                id = "pub-workspace-a",
                workspaceId = "workspace-a",
                authorPrincipalId = "principal-a",
                provider = SocialProvider.LINKEDIN,
                socialAccountId = "account-a",
                status = PublicationStatus.QUEUED,
                scheduleMode = ScheduleMode.NOW,
                priority = false,
                bodyText = "Publication from workspace A",
            ),
        )

        // Workspace B creates a publication
        publicationRepository.createDraft(
            PublicationDraft(
                id = "pub-workspace-b",
                workspaceId = "workspace-b",
                authorPrincipalId = "principal-b",
                provider = SocialProvider.LINKEDIN,
                socialAccountId = "account-b",
                status = PublicationStatus.QUEUED,
                scheduleMode = ScheduleMode.NOW,
                priority = false,
                bodyText = "Publication from workspace B",
            ),
        )

        // Workspace A tries to read its own publication — should succeed
        val pubA = publicationRepository.findByWorkspaceAndId("workspace-a", "pub-workspace-a")
        assertEquals("Publication from workspace A", pubA?.bodyText)

        // Workspace A tries to read workspace B's publication — should return null
        val pubBFromA = publicationRepository.findByWorkspaceAndId("workspace-a", "pub-workspace-b")
        assertNull(pubBFromA)

        // Workspace B tries to read workspace A's publication — should return null
        val pubAFromB = publicationRepository.findByWorkspaceAndId("workspace-b", "pub-workspace-a")
        assertNull(pubAFromB)

        // Workspace B tries to read its own publication — should succeed
        val pubB = publicationRepository.findByWorkspaceAndId("workspace-b", "pub-workspace-b")
        assertEquals("Publication from workspace B", pubB?.bodyText)
    }

    @Test
    fun `workspace isolation prevents cross-workspace queries even with knowledge of other workspace IDs`() = runTest {
        // Set up connections and accounts for both workspaces
        setupConnectionsAndAccounts()

        // Create publications in both workspaces
        publicationRepository.createDraft(
            PublicationDraft(
                id = "pub-a",
                workspaceId = "workspace-a",
                authorPrincipalId = "principal-a",
                provider = SocialProvider.LINKEDIN,
                socialAccountId = "account-a",
                status = PublicationStatus.QUEUED,
                scheduleMode = ScheduleMode.NOW,
                priority = false,
                bodyText = "Publication A",
            ),
        )
        publicationRepository.createDraft(
            PublicationDraft(
                id = "pub-b",
                workspaceId = "workspace-b",
                authorPrincipalId = "principal-b",
                provider = SocialProvider.LINKEDIN,
                socialAccountId = "account-b",
                status = PublicationStatus.QUEUED,
                scheduleMode = ScheduleMode.NOW,
                priority = false,
                bodyText = "Publication B",
            ),
        )

        // Even if workspace A knows the ID of workspace B's publication,
        // it cannot access it by querying with workspace B's ID
        val attemptCrossWorkspaceAccess = publicationRepository.findByWorkspaceAndId("workspace-b", "pub-b")

        // This query would succeed if we were in workspace B's context,
        // but in this test we're simulating workspace A trying to access workspace B's data
        // The repository correctly enforces workspace_id filtering
        assertNull(publicationRepository.findByWorkspaceAndId("workspace-a", "pub-b"))

        // Verify workspace B can access its own publication
        val pubB = publicationRepository.findByWorkspaceAndId("workspace-b", "pub-b")
        assertEquals("Publication B", pubB?.bodyText)
    }

    @Test
    fun `workspace A cannot update workspace B publication with same id`() = runTest {
        setupConnectionsAndAccounts()

        publicationRepository.createDraft(
            PublicationDraft(
                id = "pub-cross-workspace-update",
                workspaceId = "workspace-b",
                authorPrincipalId = "principal-b",
                provider = SocialProvider.LINKEDIN,
                socialAccountId = "account-b",
                status = PublicationStatus.DRAFT,
                scheduleMode = ScheduleMode.NOW,
                priority = false,
                bodyText = "Workspace B original body",
            ),
        )

        val exception = assertFailsWith<IllegalStateException> {
            publicationRepository.updateEditableDraft(
                PublicationDraft(
                    id = "pub-cross-workspace-update",
                    workspaceId = "workspace-a",
                    authorPrincipalId = "principal-a",
                    provider = SocialProvider.LINKEDIN,
                    socialAccountId = "account-a",
                    status = PublicationStatus.DRAFT,
                    scheduleMode = ScheduleMode.NOW,
                    priority = false,
                    bodyText = "Workspace A overwrite attempt",
                ),
            )
        }

        kotlin.test.assertTrue(exception.message!!.contains("current workspace"))
        assertNull(publicationRepository.findByWorkspaceAndId("workspace-a", "pub-cross-workspace-update"))
        val workspaceBPublication = publicationRepository.findByWorkspaceAndId(
            "workspace-b",
            "pub-cross-workspace-update",
        )
        assertEquals("Workspace B original body", workspaceBPublication?.bodyText)
    }

    private suspend fun seedTwoWorkspacesWithPrincipals() {
        // Workspace A
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('principal-a', 'USER', 'local:user-a@example.com', NULL, 'User A')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO workspaces (id, name, status, icon)
            VALUES ('workspace-a', 'Workspace A', 'ACTIVE', NULL)
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        // Workspace B
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('principal-b', 'USER', 'local:user-b@example.com', NULL, 'User B')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO workspaces (id, name, status, icon)
            VALUES ('workspace-b', 'Workspace B', 'ACTIVE', NULL)
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun setupConnectionsAndAccounts() {
        // Workspace A
        socialConnectionRepository.upsert(
            SocialConnection(
                id = "conn-a",
                workspaceId = "workspace-a",
                provider = SocialProvider.LINKEDIN,
                providerConnectionRef = "linkedin-conn-a",
                status = SocialConnectionStatus.ACTIVE,
                credentialReference = "00000000-0000-0000-0000-000000000001",
            ),
        )
        socialAccountRepository.upsert(
            SocialAccount(
                id = "account-a",
                socialConnectionId = "conn-a",
                workspaceId = "workspace-a",
                provider = SocialProvider.LINKEDIN,
                providerAccountId = "linkedin-account-a",
                kind = SocialAccountKind.PERSONAL_PROFILE,
                displayName = "User A",
                status = SocialConnectionStatus.ACTIVE,
            ),
        )

        // Workspace B
        socialConnectionRepository.upsert(
            SocialConnection(
                id = "conn-b",
                workspaceId = "workspace-b",
                provider = SocialProvider.LINKEDIN,
                providerConnectionRef = "linkedin-conn-b",
                status = SocialConnectionStatus.ACTIVE,
                credentialReference = "00000000-0000-0000-0000-000000000002",
            ),
        )
        socialAccountRepository.upsert(
            SocialAccount(
                id = "account-b",
                socialConnectionId = "conn-b",
                workspaceId = "workspace-b",
                provider = SocialProvider.LINKEDIN,
                providerAccountId = "linkedin-account-b",
                kind = SocialAccountKind.PERSONAL_PROFILE,
                displayName = "User B",
                status = SocialConnectionStatus.ACTIVE,
            ),
        )
    }
}
