package com.profiletailors.smp.publishing.integration

import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
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
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
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
@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PublishingWorkspaceIsolationIntegrationTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

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
                id = "00000000-0000-0000-0000-000000000401",
                workspaceId = "00000000-0000-0000-0000-00000000049f",
                provider = SocialProvider.LINKEDIN,
                providerConnectionRef = "linkedin-conn-a",
                status = SocialConnectionStatus.ACTIVE,
                credentialReference = "00000000-0000-0000-0000-000000000001",
            ),
        )

        // Workspace B creates a connection
        socialConnectionRepository.upsert(
            SocialConnection(
                id = "00000000-0000-0000-0000-000000000402",
                workspaceId = "00000000-0000-0000-0000-0000000004a0",
                provider = SocialProvider.LINKEDIN,
                providerConnectionRef = "linkedin-conn-b",
                status = SocialConnectionStatus.ACTIVE,
                credentialReference = "00000000-0000-0000-0000-000000000002",
            ),
        )

        // Workspace A tries to read its own connection — should succeed
        val connectionA = socialConnectionRepository.findByWorkspaceAndId(
            "00000000-0000-0000-0000-00000000049f",
            "00000000-0000-0000-0000-000000000401",
        )
        assertEquals("linkedin-conn-a", connectionA?.providerConnectionRef)

        // Workspace A tries to read workspace B's connection — should return null
        val connectionBFromA = socialConnectionRepository.findByWorkspaceAndId(
            "00000000-0000-0000-0000-00000000049f",
            "00000000-0000-0000-0000-000000000402",
        )
        assertNull(connectionBFromA)

        // Workspace B tries to read workspace A's connection — should return null
        val connectionAFromB = socialConnectionRepository.findByWorkspaceAndId(
            "00000000-0000-0000-0000-0000000004a0",
            "00000000-0000-0000-0000-000000000401",
        )
        assertNull(connectionAFromB)

        // Workspace B tries to read its own connection — should succeed
        val connectionB = socialConnectionRepository.findByWorkspaceAndId(
            "00000000-0000-0000-0000-0000000004a0",
            "00000000-0000-0000-0000-000000000402",
        )
        assertEquals("linkedin-conn-b", connectionB?.providerConnectionRef)
    }

    @Test
    fun `workspace A cannot see accounts from workspace B`() = runTest {
        setupConnectionsAndAccounts(
            accountAId = "00000000-0000-0000-0000-0000000003eb",
            accountBId = "00000000-0000-0000-0000-0000000003ec",
        )

        // Workspace A tries to read its own account — should succeed
        val accountA = socialAccountRepository.findByWorkspaceAndId(
            "00000000-0000-0000-0000-00000000049f",
            "00000000-0000-0000-0000-0000000003eb",
        )
        assertEquals("linkedin-account-a", accountA?.providerAccountId)

        // Workspace A tries to read workspace B's account — should return null
        val accountBFromA = socialAccountRepository.findByWorkspaceAndId(
            "00000000-0000-0000-0000-00000000049f",
            "00000000-0000-0000-0000-0000000003ec",
        )
        assertNull(accountBFromA)

        // Workspace B tries to read workspace A's account — should return null
        val accountAFromB = socialAccountRepository.findByWorkspaceAndId(
            "00000000-0000-0000-0000-0000000004a0",
            "00000000-0000-0000-0000-0000000003eb",
        )
        assertNull(accountAFromB)

        // Workspace B tries to read its own account — should succeed
        val accountB = socialAccountRepository.findByWorkspaceAndId(
            "00000000-0000-0000-0000-0000000004a0",
            "00000000-0000-0000-0000-0000000003ec",
        )
        assertEquals("linkedin-account-b", accountB?.providerAccountId)
    }

    @Test
    fun `workspace A cannot see publications from workspace B`() = runTest {
        // Set up connections and accounts for both workspaces
        setupConnectionsAndAccounts()

        // Workspace A creates a publication
        publicationRepository.createDraft(
            PublicationDraft(
                id = "00000000-0000-0000-0000-00000000047e",
                workspaceId = "00000000-0000-0000-0000-00000000049f",
                authorPrincipalId = "00000000-0000-0000-0000-000000000432",
                provider = SocialProvider.LINKEDIN,
                socialAccountId = "00000000-0000-0000-0000-0000000003e9",
                status = PublicationStatus.QUEUED,
                scheduleMode = ScheduleMode.NOW,
                priority = false,
                bodyText = "Publication from workspace A",
            ),
        )

        // Workspace B creates a publication
        publicationRepository.createDraft(
            PublicationDraft(
                id = "00000000-0000-0000-0000-00000000047f",
                workspaceId = "00000000-0000-0000-0000-0000000004a0",
                authorPrincipalId = "00000000-0000-0000-0000-000000000433",
                provider = SocialProvider.LINKEDIN,
                socialAccountId = "00000000-0000-0000-0000-0000000003ea",
                status = PublicationStatus.QUEUED,
                scheduleMode = ScheduleMode.NOW,
                priority = false,
                bodyText = "Publication from workspace B",
            ),
        )

        // Workspace A tries to read its own publication — should succeed
        val pubA = publicationRepository.findByWorkspaceAndId(
            "00000000-0000-0000-0000-00000000049f",
            "00000000-0000-0000-0000-00000000047e",
        )
        assertEquals("Publication from workspace A", pubA?.bodyText)

        // Workspace A tries to read workspace B's publication — should return null
        val pubBFromA = publicationRepository.findByWorkspaceAndId(
            "00000000-0000-0000-0000-00000000049f",
            "00000000-0000-0000-0000-00000000047f",
        )
        assertNull(pubBFromA)

        // Workspace B tries to read workspace A's publication — should return null
        val pubAFromB = publicationRepository.findByWorkspaceAndId(
            "00000000-0000-0000-0000-0000000004a0",
            "00000000-0000-0000-0000-00000000047e",
        )
        assertNull(pubAFromB)

        // Workspace B tries to read its own publication — should succeed
        val pubB = publicationRepository.findByWorkspaceAndId(
            "00000000-0000-0000-0000-0000000004a0",
            "00000000-0000-0000-0000-00000000047f",
        )
        assertEquals("Publication from workspace B", pubB?.bodyText)
    }

    @Test
    fun `workspace isolation prevents cross-workspace queries even with knowledge of other workspace IDs`() = runTest {
        // Set up connections and accounts for both workspaces
        setupConnectionsAndAccounts()

        // Create publications in both workspaces
        publicationRepository.createDraft(
            PublicationDraft(
                id = "00000000-0000-0000-0000-000000000435",
                workspaceId = "00000000-0000-0000-0000-00000000049f",
                authorPrincipalId = "00000000-0000-0000-0000-000000000432",
                provider = SocialProvider.LINKEDIN,
                socialAccountId = "00000000-0000-0000-0000-0000000003e9",
                status = PublicationStatus.QUEUED,
                scheduleMode = ScheduleMode.NOW,
                priority = false,
                bodyText = "Publication A",
            ),
        )
        publicationRepository.createDraft(
            PublicationDraft(
                id = "00000000-0000-0000-0000-000000000436",
                workspaceId = "00000000-0000-0000-0000-0000000004a0",
                authorPrincipalId = "00000000-0000-0000-0000-000000000433",
                provider = SocialProvider.LINKEDIN,
                socialAccountId = "00000000-0000-0000-0000-0000000003ea",
                status = PublicationStatus.QUEUED,
                scheduleMode = ScheduleMode.NOW,
                priority = false,
                bodyText = "Publication B",
            ),
        )

        // Even if workspace A knows the ID of workspace B's publication,
        // it cannot access it by querying with workspace B's ID
        val attemptCrossWorkspaceAccess = publicationRepository.findByWorkspaceAndId(
            "00000000-0000-0000-0000-0000000004a0",
            "00000000-0000-0000-0000-000000000436",
        )

        // This query would succeed if we were in workspace B's context,
        // but in this test we're simulating workspace A trying to access workspace B's data
        // The repository correctly enforces workspace_id filtering
        assertNull(
            publicationRepository.findByWorkspaceAndId(
                "00000000-0000-0000-0000-00000000049f",
                "00000000-0000-0000-0000-000000000436",
            ),
        )

        // Verify workspace B can access its own publication
        val pubB = publicationRepository.findByWorkspaceAndId(
            "00000000-0000-0000-0000-0000000004a0",
            "00000000-0000-0000-0000-000000000436",
        )
        assertEquals("Publication B", pubB?.bodyText)
    }

    @Test
    fun `workspace A cannot update workspace B publication with same id`() = runTest {
        setupConnectionsAndAccounts()

        publicationRepository.createDraft(
            PublicationDraft(
                id = "00000000-0000-0000-0000-000000000444",
                workspaceId = "00000000-0000-0000-0000-0000000004a0",
                authorPrincipalId = "00000000-0000-0000-0000-000000000433",
                provider = SocialProvider.LINKEDIN,
                socialAccountId = "00000000-0000-0000-0000-0000000003ea",
                status = PublicationStatus.DRAFT,
                scheduleMode = ScheduleMode.NOW,
                priority = false,
                bodyText = "Workspace B original body",
            ),
        )

        val exception = assertFailsWith<IllegalStateException> {
            publicationRepository.updateEditableDraft(
                PublicationDraft(
                    id = "00000000-0000-0000-0000-000000000444",
                    workspaceId = "00000000-0000-0000-0000-00000000049f",
                    authorPrincipalId = "00000000-0000-0000-0000-000000000432",
                    provider = SocialProvider.LINKEDIN,
                    socialAccountId = "00000000-0000-0000-0000-0000000003e9",
                    status = PublicationStatus.DRAFT,
                    scheduleMode = ScheduleMode.NOW,
                    priority = false,
                    bodyText = "Workspace A overwrite attempt",
                ),
            )
        }

        kotlin.test.assertTrue(exception.message!!.contains("current workspace"))
        assertNull(
            publicationRepository.findByWorkspaceAndId(
                "00000000-0000-0000-0000-00000000049f",
                "00000000-0000-0000-0000-000000000444",
            ),
        )
        val workspaceBPublication = publicationRepository.findByWorkspaceAndId(
            "00000000-0000-0000-0000-0000000004a0",
            "00000000-0000-0000-0000-000000000444",
        )
        assertEquals("Workspace B original body", workspaceBPublication?.bodyText)
    }

    private suspend fun seedTwoWorkspacesWithPrincipals() {
        // Workspace A
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('00000000-0000-0000-0000-000000000432', 'USER', 'local:user-a@example.com', NULL, 'User A')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO workspaces (id, name, status, icon)
            VALUES ('00000000-0000-0000-0000-00000000049f', 'Workspace A', 'ACTIVE', NULL)
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        // Workspace B
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('00000000-0000-0000-0000-000000000433', 'USER', 'local:user-b@example.com', NULL, 'User B')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO workspaces (id, name, status, icon)
            VALUES ('00000000-0000-0000-0000-0000000004a0', 'Workspace B', 'ACTIVE', NULL)
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun setupConnectionsAndAccounts(
        accountAId: String = "00000000-0000-0000-0000-0000000003e9",
        accountBId: String = "00000000-0000-0000-0000-0000000003ea",
    ) {
        // Workspace A
        socialConnectionRepository.upsert(
            SocialConnection(
                id = "00000000-0000-0000-0000-0000000003ff",
                workspaceId = "00000000-0000-0000-0000-00000000049f",
                provider = SocialProvider.LINKEDIN,
                providerConnectionRef = "linkedin-conn-a",
                status = SocialConnectionStatus.ACTIVE,
                credentialReference = "00000000-0000-0000-0000-000000000001",
            ),
        )
        socialAccountRepository.upsert(
            SocialAccount(
                id = accountAId,
                socialConnectionId = "00000000-0000-0000-0000-0000000003ff",
                workspaceId = "00000000-0000-0000-0000-00000000049f",
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
                id = "00000000-0000-0000-0000-000000000400",
                workspaceId = "00000000-0000-0000-0000-0000000004a0",
                provider = SocialProvider.LINKEDIN,
                providerConnectionRef = "linkedin-conn-b",
                status = SocialConnectionStatus.ACTIVE,
                credentialReference = "00000000-0000-0000-0000-000000000002",
            ),
        )
        socialAccountRepository.upsert(
            SocialAccount(
                id = accountBId,
                socialConnectionId = "00000000-0000-0000-0000-000000000400",
                workspaceId = "00000000-0000-0000-0000-0000000004a0",
                provider = SocialProvider.LINKEDIN,
                providerAccountId = "linkedin-account-b",
                kind = SocialAccountKind.PERSONAL_PROFILE,
                displayName = "User B",
                status = SocialConnectionStatus.ACTIVE,
            ),
        )
    }

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("publishing_workspace_isolation")
    }
}
