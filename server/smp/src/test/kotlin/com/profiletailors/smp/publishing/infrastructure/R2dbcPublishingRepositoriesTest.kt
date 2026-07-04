package com.profiletailors.smp.publishing.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.media.infrastructure.persistence.R2dbcMediaAssetRepository
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialConnection
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcConnectedSocialChannelReadRepository
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcPublicationAssetRepository
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcPublicationRepository
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcSocialAccountRepository
import com.profiletailors.smp.publishing.infrastructure.persistence.R2dbcSocialConnectionRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcPublishingRepositoriesTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    private lateinit var socialConnectionRepository: R2dbcSocialConnectionRepository
    private lateinit var socialAccountRepository: R2dbcSocialAccountRepository
    private lateinit var connectedSocialChannelReadRepository: R2dbcConnectedSocialChannelReadRepository
    private lateinit var publicationRepository: R2dbcPublicationRepository
    private lateinit var publicationAssetRepository: R2dbcPublicationAssetRepository
    private lateinit var mediaAssetRepository: R2dbcMediaAssetRepository
    private lateinit var meterRegistry: SimpleMeterRegistry

    @BeforeEach
    fun setUpRepositories() = runTest {
        seedPrincipalAndWorkspace()
        meterRegistry = SimpleMeterRegistry()
        socialConnectionRepository = R2dbcSocialConnectionRepository(databaseClient)
        socialAccountRepository = R2dbcSocialAccountRepository(databaseClient, meterRegistry)
        connectedSocialChannelReadRepository = R2dbcConnectedSocialChannelReadRepository(databaseClient)
        publicationRepository = R2dbcPublicationRepository(databaseClient, transactionalOperator)
        publicationAssetRepository = R2dbcPublicationAssetRepository(databaseClient, ObjectMapper())
        mediaAssetRepository = R2dbcMediaAssetRepository(databaseClient)
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
    fun `social connection upsert preserves id on conflict`() = runTest {
        val first = socialConnectionRepository.upsert(
            SocialConnection(
                id = "soconn-original",
                workspaceId = "workspace-1",
                provider = SocialProvider.LINKEDIN,
                providerConnectionRef = "linkedin-conn-conflict",
                status = SocialConnectionStatus.ACTIVE,
                credentialReference = "credential-original",
                connectedAt = Instant.parse("2026-06-12T12:00:00Z"),
            ),
        )

        val updated = socialConnectionRepository.upsert(
            SocialConnection(
                id = "soconn-reconnect",
                workspaceId = "workspace-1",
                provider = SocialProvider.LINKEDIN,
                providerConnectionRef = "linkedin-conn-conflict",
                status = SocialConnectionStatus.REVOKED,
                credentialReference = "credential-updated",
                connectedAt = Instant.parse("2026-06-13T12:00:00Z"),
                lastSyncedAt = Instant.parse("2026-06-13T12:05:00Z"),
            ),
        )

        assertEquals(first.id, updated.id)
        assertEquals("credential-updated", updated.credentialReference)
        assertEquals(SocialConnectionStatus.REVOKED, updated.status)
    }

    @Test
    fun `social account upsert preserves id on conflict`() = runTest {
        val connection = socialConnectionRepository.upsert(
            SocialConnection(
                id = "soconn-account-conflict",
                workspaceId = "workspace-1",
                provider = SocialProvider.LINKEDIN,
                providerConnectionRef = "linkedin-conn-account-conflict",
                status = SocialConnectionStatus.ACTIVE,
                credentialReference = "credential-ref",
            ),
        )
        val first = socialAccountRepository.upsert(
            SocialAccount(
                id = "soacc-original",
                socialConnectionId = connection.id,
                workspaceId = "workspace-1",
                provider = SocialProvider.LINKEDIN,
                providerAccountId = "linkedin-account-conflict",
                kind = SocialAccountKind.PERSONAL_PROFILE,
                displayName = "Original Name",
                status = SocialConnectionStatus.ACTIVE,
            ),
        )

        val updated = socialAccountRepository.upsert(
            first.copy(
                id = "soacc-reconnect",
                displayName = "Updated Name",
                profileUrn = "urn:li:person:updated",
                status = SocialConnectionStatus.REVOKED,
            ),
        )

        assertEquals(first.id, updated.id)
        assertEquals("Updated Name", updated.displayName)
        assertEquals("urn:li:person:updated", updated.profileUrn)
        assertEquals(SocialConnectionStatus.REVOKED, updated.status)
    }

    @Test
    fun `connected channel read repository returns safe active joined rows`() = runTest {
        val connection = socialConnectionRepository.upsert(
            SocialConnection(
                id = "soconn-read",
                workspaceId = "workspace-1",
                provider = SocialProvider.LINKEDIN,
                providerConnectionRef = "linkedin-conn-read",
                status = SocialConnectionStatus.ACTIVE,
                credentialReference = "credential-secret-not-exposed",
                connectedAt = Instant.parse("2026-06-12T12:00:00Z"),
            ),
        )
        socialAccountRepository.upsert(
            SocialAccount(
                id = "soacc-read",
                socialConnectionId = connection.id,
                workspaceId = "workspace-1",
                provider = SocialProvider.LINKEDIN,
                providerAccountId = "linkedin-account-read",
                kind = SocialAccountKind.PERSONAL_PROFILE,
                displayName = "Read Model Name",
                profileUrn = "urn:li:person:read",
                status = SocialConnectionStatus.ACTIVE,
            ),
        )

        val channels = connectedSocialChannelReadRepository.listByWorkspace("workspace-1")

        assertEquals(1, channels.size)
        val channel = channels.single()
        assertEquals("soacc-read", channel.socialAccountId)
        assertEquals(connection.id, channel.connectionId)
        assertEquals(SocialProvider.LINKEDIN, channel.provider)
        assertEquals(SocialAccountKind.PERSONAL_PROFILE, channel.accountKind)
        assertEquals("Read Model Name", channel.displayName)
        assertEquals(SocialConnectionStatus.ACTIVE, channel.status)
        assertEquals("urn:li:person:read", channel.profileUrn)
    }

    @Test
    @Suppress("LongMethod")
    fun `persists publication with asset links and reads it back`() = runTest {
        databaseClient.sql(
            """
            INSERT INTO social_connections (
                id, workspace_id, provider, provider_connection_ref, status, credential_reference
            ) VALUES (
                'soconn-1', 'workspace-1', 'LINKEDIN', 'linkedin-conn-1',
                'ACTIVE', '00000000-0000-0000-0000-000000000000'
            )
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO social_accounts (
                id, social_connection_id, workspace_id, provider, provider_account_id,
                account_type, display_name, status
            ) VALUES (
                'soacc-1', 'soconn-1', 'workspace-1', 'LINKEDIN',
                'linkedin-account-1', 'PERSONAL_PROFILE', 'Yuniel', 'ACTIVE'
            )
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO workspace_file_blobs (workspace_id, file_hash, storage_key, detected_media_type, file_size_bytes, status)
            VALUES (
                'workspace-1', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                'storage/key.png', 'image/png', 1024, 'READY'
            )
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO media_assets (
                asset_id, workspace_id, source_type, media_type, storage_key,
                original_filename, file_size_bytes, status, upload_started_at, created_at,
                file_hash, detected_media_type
            ) VALUES (
                'asset-1', 'workspace-1', 'UPLOADED', 'image/png', 'storage/key.png',
                'asset-1.png', 1024, 'READY', NULL, CURRENT_TIMESTAMP,
                'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 'image/png'
            )
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

        assertNotNull(loaded)
        assertEquals(listOf("asset-1"), loaded?.assetIds)
    }

    @Test
    fun `media asset repository finds assets by workspace and ids`() = runTest {
        databaseClient.sql(
            """
            INSERT INTO workspace_file_blobs (workspace_id, file_hash, storage_key, detected_media_type, file_size_bytes, status)
            VALUES
                ('workspace-1', 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', 'storage/key.png', 'image/png', 1024, 'READY'),
                ('workspace-1', 'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc', 'storage/key-2.jpg', 'image/jpeg', 2048, 'READY')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO media_assets (
                asset_id, workspace_id, source_type, media_type, storage_key,
                original_filename, file_size_bytes, status, upload_started_at, created_at,
                file_hash, detected_media_type
            ) VALUES (
                'media-asset-1', 'workspace-1', 'UPLOADED', 'image/png', 'storage/key.png',
                'hero.png', 1024, 'READY', NULL, CURRENT_TIMESTAMP,
                'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', 'image/png'
            )
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO media_assets (
                asset_id, workspace_id, source_type, media_type, storage_key,
                original_filename, file_size_bytes, status, upload_started_at, created_at,
                file_hash, detected_media_type
            ) VALUES (
                'media-asset-2', 'workspace-1', 'UPLOADED', 'image/jpeg', 'storage/key-2.jpg',
                'second.jpg', 2048, 'READY', NULL, CURRENT_TIMESTAMP,
                'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc', 'image/jpeg'
            )
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        val assets = mediaAssetRepository.findByWorkspaceAndIds(
            "workspace-1",
            listOf("media-asset-1"),
        )

        assertEquals(1, assets.size)
        assertEquals("media-asset-1", assets.single().assetId)
        assertEquals("image/png", assets.single().mediaType)
    }

    @Test
    fun `findInDateRange returns publications within date range`() = runTest {
        seedSocialAccount()
        val pub1 = publicationRepository.createDraft(
            PublicationDraft(
                id = "pub-range-1", workspaceId = "workspace-1", authorPrincipalId = "principal-1",
                provider = SocialProvider.LINKEDIN, socialAccountId = "soacc-1",
                status = PublicationStatus.SCHEDULED, scheduleMode = ScheduleMode.SCHEDULED_AT, priority = false,
                bodyText = "June post", scheduledFor = Instant.parse("2026-06-15T12:00:00Z"),
            ),
        )
        val pub2 = publicationRepository.createDraft(
            PublicationDraft(
                id = "pub-range-2", workspaceId = "workspace-1", authorPrincipalId = "principal-1",
                provider = SocialProvider.LINKEDIN, socialAccountId = "soacc-1",
                status = PublicationStatus.SCHEDULED, scheduleMode = ScheduleMode.SCHEDULED_AT, priority = false,
                bodyText = "July post", scheduledFor = Instant.parse("2026-07-01T12:00:00Z"),
            ),
        )

        val results = publicationRepository.findInDateRange(
            workspaceId = "workspace-1",
            from = Instant.parse("2026-06-01T00:00:00Z"),
            to = Instant.parse("2026-07-01T00:00:00Z"),
        )

        assertEquals(listOf(pub1.id), results.map { it.id })
    }

    @Test
    fun `findInDateRange filters by status`() = runTest {
        seedSocialAccount()
        val draftPub = publicationRepository.createDraft(
            PublicationDraft(
                id = "pub-status-draft", workspaceId = "workspace-1", authorPrincipalId = "principal-1",
                provider = SocialProvider.LINKEDIN, socialAccountId = "soacc-1",
                status = PublicationStatus.DRAFT, scheduleMode = ScheduleMode.SCHEDULED_AT, priority = false,
                bodyText = "Draft post", scheduledFor = Instant.parse("2026-06-15T12:00:00Z"),
            ),
        )
        val scheduledPub = publicationRepository.createDraft(
            PublicationDraft(
                id = "pub-status-sched", workspaceId = "workspace-1", authorPrincipalId = "principal-1",
                provider = SocialProvider.LINKEDIN, socialAccountId = "soacc-1",
                status = PublicationStatus.SCHEDULED, scheduleMode = ScheduleMode.SCHEDULED_AT, priority = false,
                bodyText = "Scheduled post", scheduledFor = Instant.parse("2026-06-15T14:00:00Z"),
            ),
        )

        val results = publicationRepository.findInDateRange(
            workspaceId = "workspace-1",
            from = Instant.parse("2026-06-01T00:00:00Z"),
            to = Instant.parse("2026-07-01T00:00:00Z"),
            statuses = setOf(PublicationStatus.SCHEDULED),
        )

        assertEquals(listOf(scheduledPub.id), results.map { it.id })
    }

    @Test
    fun `findInDateRange returns empty list for no matches`() = runTest {
        val results = publicationRepository.findInDateRange(
            workspaceId = "workspace-1",
            from = Instant.parse("2025-01-01T00:00:00Z"),
            to = Instant.parse("2025-01-31T00:00:00Z"),
        )
        assertTrue(results.isEmpty())
    }

    @Test
    fun `countByDate returns publications for date range`() = runTest {
        seedSocialAccount()
        publicationRepository.createDraft(
            PublicationDraft(
                id = "pub-count-1", workspaceId = "workspace-1", authorPrincipalId = "principal-1",
                provider = SocialProvider.LINKEDIN, socialAccountId = "soacc-1",
                status = PublicationStatus.SCHEDULED, scheduleMode = ScheduleMode.SCHEDULED_AT, priority = false,
                bodyText = "Count post", scheduledFor = Instant.parse("2026-06-15T12:00:00Z"),
            ),
        )

        val results = publicationRepository.countByDate(
            workspaceId = "workspace-1",
            from = Instant.parse("2026-06-01T00:00:00Z"),
            to = Instant.parse("2026-07-01T00:00:00Z"),
        )

        assertEquals(1, results.size)
        assertEquals(1, results.single().count)
        assertEquals(java.time.LocalDate.parse("2026-06-15"), results.single().date)
    }

    @Test
    fun `findInDateRange filters by socialAccountIds`() = runTest {
        seedSocialAccount()
        databaseClient.sql(
            """
            INSERT INTO social_connections (
                id, workspace_id, provider, provider_connection_ref, status, credential_reference
            ) VALUES (
                'soconn-2', 'workspace-1', 'LINKEDIN', 'linkedin-conn-2',
                'ACTIVE', '00000000-0000-0000-0000-000000000000'
            )
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO social_accounts (
                id, social_connection_id, workspace_id, provider, provider_account_id,
                account_type, display_name, status
            ) VALUES (
                'soacc-2', 'soconn-2', 'workspace-1', 'LINKEDIN',
                'linkedin-account-2', 'PERSONAL_PROFILE', 'Another', 'ACTIVE'
            )
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        val pub1 = publicationRepository.createDraft(
            PublicationDraft(
                id = "pub-filter-1", workspaceId = "workspace-1", authorPrincipalId = "principal-1",
                provider = SocialProvider.LINKEDIN, socialAccountId = "soacc-1",
                status = PublicationStatus.SCHEDULED, scheduleMode = ScheduleMode.SCHEDULED_AT, priority = false,
                bodyText = "First account", scheduledFor = Instant.parse("2026-06-15T12:00:00Z"),
            ),
        )
        val pub2 = publicationRepository.createDraft(
            PublicationDraft(
                id = "pub-filter-2", workspaceId = "workspace-1", authorPrincipalId = "principal-1",
                provider = SocialProvider.LINKEDIN, socialAccountId = "soacc-2",
                status = PublicationStatus.SCHEDULED, scheduleMode = ScheduleMode.SCHEDULED_AT, priority = false,
                bodyText = "Second account", scheduledFor = Instant.parse("2026-06-15T14:00:00Z"),
            ),
        )

        val results = publicationRepository.findInDateRange(
            workspaceId = "workspace-1",
            from = Instant.parse("2026-06-01T00:00:00Z"),
            to = Instant.parse("2026-07-01T00:00:00Z"),
            socialAccountIds = setOf("soacc-1"),
        )

        assertEquals(listOf(pub1.id), results.map { it.id })
    }

    @Test
    fun `countByDate groups by requested timezone`() = runTest {
        seedSocialAccount()
        publicationRepository.createDraft(
            PublicationDraft(
                id = "pub-tz-1", workspaceId = "workspace-1", authorPrincipalId = "principal-1",
                provider = SocialProvider.LINKEDIN, socialAccountId = "soacc-1",
                status = PublicationStatus.SCHEDULED, scheduleMode = ScheduleMode.SCHEDULED_AT, priority = false,
                bodyText = "Late UTC", scheduledFor = Instant.parse("2026-06-09T23:00:00Z"),
            ),
        )
        publicationRepository.createDraft(
            PublicationDraft(
                id = "pub-tz-2", workspaceId = "workspace-1", authorPrincipalId = "principal-1",
                provider = SocialProvider.LINKEDIN, socialAccountId = "soacc-1",
                status = PublicationStatus.SCHEDULED, scheduleMode = ScheduleMode.SCHEDULED_AT, priority = false,
                bodyText = "Early UTC", scheduledFor = Instant.parse("2026-06-10T01:00:00Z"),
            ),
        )

        val results = publicationRepository.countByDate(
            workspaceId = "workspace-1",
            from = Instant.parse("2026-06-09T00:00:00Z"),
            to = Instant.parse("2026-06-11T00:00:00Z"),
            timezone = "America/New_York",
        )

        assertEquals(1, results.size)
        assertEquals(java.time.LocalDate.parse("2026-06-09"), results.single().date)
        assertEquals(2, results.single().count)
    }

    @Test
    fun `upsert increments avatar persisted counter when avatarUrl is present`() = runTest {
        val connection = socialConnectionRepository.upsert(
            SocialConnection(
                id = "soconn-avatar",
                workspaceId = "workspace-1",
                provider = SocialProvider.LINKEDIN,
                providerConnectionRef = "linkedin-conn-avatar",
                status = SocialConnectionStatus.ACTIVE,
                credentialReference = "credential-ref",
            ),
        )

        val counterBefore = avatarPersistedCounterValue()

        socialAccountRepository.upsert(
            SocialAccount(
                id = "soacc-avatar-1",
                socialConnectionId = connection.id,
                workspaceId = "workspace-1",
                provider = SocialProvider.LINKEDIN,
                providerAccountId = "linkedin-avatar-account-1",
                kind = SocialAccountKind.PERSONAL_PROFILE,
                displayName = "Avatar User",
                avatarUrl = "https://media.licdn.com/photo.jpg",
                status = SocialConnectionStatus.ACTIVE,
            ),
        )

        assertEquals(counterBefore + 1.0, avatarPersistedCounterValue(), 0.001)
    }

    @Test
    fun `upsert does not increment avatar persisted counter when avatarUrl is null`() = runTest {
        val connection = socialConnectionRepository.upsert(
            SocialConnection(
                id = "soconn-no-avatar",
                workspaceId = "workspace-1",
                provider = SocialProvider.LINKEDIN,
                providerConnectionRef = "linkedin-conn-no-avatar",
                status = SocialConnectionStatus.ACTIVE,
                credentialReference = "credential-ref",
            ),
        )

        val counterBefore = avatarPersistedCounterValue()

        socialAccountRepository.upsert(
            SocialAccount(
                id = "soacc-no-avatar",
                socialConnectionId = connection.id,
                workspaceId = "workspace-1",
                provider = SocialProvider.LINKEDIN,
                providerAccountId = "linkedin-no-avatar-account",
                kind = SocialAccountKind.PERSONAL_PROFILE,
                displayName = "No Avatar User",
                avatarUrl = null,
                status = SocialConnectionStatus.ACTIVE,
            ),
        )

        assertEquals(counterBefore, avatarPersistedCounterValue(), 0.001)
    }

    private fun avatarPersistedCounterValue(): Double = meterRegistry.find("publishing.linkedin.avatar.persisted")
        .counter()?.count() ?: 0.0

    private suspend fun seedSocialAccount() {
        databaseClient.sql(
            """
            INSERT INTO social_connections (
                id, workspace_id, provider, provider_connection_ref, status, credential_reference
            ) VALUES (
                'soconn-1', 'workspace-1', 'LINKEDIN', 'linkedin-conn-1',
                'ACTIVE', '00000000-0000-0000-0000-000000000000'
            )
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO social_accounts (
                id, social_connection_id, workspace_id, provider, provider_account_id,
                account_type, display_name, status
            ) VALUES (
                'soacc-1', 'soconn-1', 'workspace-1', 'LINKEDIN',
                'linkedin-account-1', 'PERSONAL_PROFILE', 'Yuniel', 'ACTIVE'
            )
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
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
            INSERT INTO workspaces (id, name, status, icon)
            VALUES ('workspace-1', 'Workspace 1', 'ACTIVE', NULL)
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
    }

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("publishing_repositories")
    }
}
