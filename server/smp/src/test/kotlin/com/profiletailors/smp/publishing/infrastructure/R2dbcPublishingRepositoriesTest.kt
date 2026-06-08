package com.profiletailors.smp.publishing.infrastructure

import com.profiletailors.smp.integration.support.DatabaseUnitTestBase
import com.profiletailors.smp.publishing.domain.AssetSourceType
import com.profiletailors.smp.publishing.domain.PublicationAsset
import com.profiletailors.smp.publishing.domain.PublicationAssetStatus
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialConnection
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcPublicationAssetRepository
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcPublicationRepository
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcSocialAccountRepository
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcSocialConnectionRepository
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class R2dbcPublishingRepositoriesTest : DatabaseUnitTestBase() {

    override fun databaseName(): String = "publishing_repositories"

    private lateinit var socialConnectionRepository: R2dbcSocialConnectionRepository
    private lateinit var socialAccountRepository: R2dbcSocialAccountRepository
    private lateinit var publicationRepository: R2dbcPublicationRepository
    private lateinit var publicationAssetRepository: R2dbcPublicationAssetRepository

    @BeforeEach
    fun setUpRepositories() = runTest {
        seedPrincipalAndWorkspace()
        socialConnectionRepository = R2dbcSocialConnectionRepository(databaseClient)
        socialAccountRepository = R2dbcSocialAccountRepository(databaseClient)
        publicationRepository = R2dbcPublicationRepository(databaseClient)
        publicationAssetRepository = R2dbcPublicationAssetRepository(databaseClient)
    }

    @Test
    fun `persists and reads social connection and account`() = runTest {
        val connection = socialConnectionRepository.upsert(
            SocialConnection(
                id = "soconn-1",
                workspaceId = "workspace-1",
                provider = SocialProvider.LINKEDIN,
                providerConnectionRef = "linkedin-conn-1",
                status = SocialConnectionStatus.ACTIVE,
                credentialReference = "00000000-0000-0000-0000-000000000000",
            ),
        )
        val account = socialAccountRepository.upsert(
            SocialAccount(
                id = "soacc-1",
                socialConnectionId = connection.id,
                workspaceId = "workspace-1",
                provider = SocialProvider.LINKEDIN,
                providerAccountId = "linkedin-account-1",
                kind = SocialAccountKind.PERSONAL_PROFILE,
                displayName = "Yuniel",
                status = SocialConnectionStatus.ACTIVE,
            ),
        )

        val loadedConnection = socialConnectionRepository.findByWorkspaceAndId("workspace-1", "soconn-1")
        val loadedAccount = socialAccountRepository.findByWorkspaceAndId("workspace-1", "soacc-1")

        assertEquals("linkedin-conn-1", loadedConnection?.providerConnectionRef)
        assertEquals("linkedin-account-1", loadedAccount?.providerAccountId)
        assertEquals(SocialAccountKind.PERSONAL_PROFILE, loadedAccount?.kind)
    }

    @Test
    fun `persists publication with asset links and reads it back`() = runTest {
        databaseClient.sql(
            """
            INSERT INTO social_connections (id, workspace_id, provider, provider_connection_ref, status, credential_reference)
            VALUES ('soconn-1', 'workspace-1', 'LINKEDIN', 'linkedin-conn-1', 'ACTIVE', '00000000-0000-0000-0000-000000000000')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO social_accounts (id, social_connection_id, workspace_id, provider, provider_account_id, account_type, display_name, status)
            VALUES ('soacc-1', 'soconn-1', 'workspace-1', 'LINKEDIN', 'linkedin-account-1', 'PERSONAL_PROFILE', 'Yuniel', 'ACTIVE')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO publication_assets (id, workspace_id, source_type, media_type, storage_key, status, created_by_principal_id)
            VALUES ('asset-1', 'workspace-1', 'UPLOADED', 'image/png', 'storage/key.png', 'READY', 'principal-1')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        val persisted = publicationRepository.createDraft(
            PublicationDraft(
                id = "pub-1",
                workspaceId = "workspace-1",
                authorPrincipalId = "principal-1",
                provider = SocialProvider.LINKEDIN,
                socialAccountId = "soacc-1",
                status = PublicationStatus.QUEUED,
                scheduleMode = ScheduleMode.NOW,
                priority = true,
                bodyText = "Hello world",
                assetIds = listOf("asset-1"),
            ),
        )

        val loaded = publicationRepository.findByWorkspaceAndId("workspace-1", persisted.id)
        val assets = publicationAssetRepository.findByWorkspaceAndIds("workspace-1", listOf("asset-1"))

        assertNotNull(loaded)
        assertEquals(listOf("asset-1"), loaded?.assetIds)
        assertEquals("image/png", assets.single().mediaType)
    }

    private suspend fun seedPrincipalAndWorkspace() {
        databaseClient.sql(
            """
            INSERT INTO principals (id, principal_type, subject, provider, display_identity)
            VALUES ('principal-1', 'USER', 'local:owner@example.com', NULL, 'owner')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO workspaces (id, name, status)
            VALUES ('workspace-1', 'Workspace 1', 'ACTIVE')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
    }
}
