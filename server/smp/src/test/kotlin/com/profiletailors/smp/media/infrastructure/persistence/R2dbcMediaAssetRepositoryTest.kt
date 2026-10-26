package com.profiletailors.smp.media.infrastructure.persistence

import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.media.domain.MediaAsset
import com.profiletailors.smp.media.domain.MediaAssetStatus
import com.profiletailors.smp.media.domain.MediaSourceType
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

/**
 * PostgreSQL-backed tests for [R2dbcMediaAssetRepository]. Exercises the real schema constraints
 * used by `findActiveByWorkspaceAndHash`; PostgreSQL-specific `ON CONFLICT` and locking clauses stay
 * covered by [R2dbcMediaRepositoriesPostgresTest].
 */
@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcMediaAssetRepositoryTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    private lateinit var mediaRepository: R2dbcMediaAssetRepository

    private fun nextWorkspaceId(): String = UUID.randomUUID().toString()

    @BeforeEach
    fun setUp() = runTest {
        mediaRepository = R2dbcMediaAssetRepository(databaseClient)
    }

    private suspend fun seedWorkspace(workspaceId: String) {
        databaseClient.sql(
            """
            INSERT INTO workspaces (id, name, status, icon)
            VALUES (:workspaceId, 'Workspace', 'ACTIVE', NULL)
            """.trimIndent(),
        )
            .bind("workspaceId", UUID.fromString(workspaceId))
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun seedBlob(workspaceId: String, fileHash: String) {
        databaseClient.sql(
            """
            INSERT INTO workspace_file_blobs (workspace_id, file_hash, storage_key, detected_media_type, file_size_bytes, status)
            VALUES (:workspaceId, :fileHash, :storageKey, 'image/png', 42, 'READY')
            """.trimIndent(),
        )
            .bind("workspaceId", UUID.fromString(workspaceId))
            .bind("fileHash", fileHash)
            .bind("storageKey", "media/$workspaceId/$fileHash")
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    @Test
    fun `findActiveByWorkspaceAndHash returns the active asset that references the hash`() = runTest {
        val workspaceId = nextWorkspaceId()
        seedWorkspace(workspaceId)
        seedBlob(workspaceId, HASH_A)
        seedBlob(workspaceId, HASH_B)
        insertAsset(workspaceId = workspaceId, assetId = ASSET_A, fileHash = HASH_A, status = "READY")
        insertAsset(workspaceId = workspaceId, assetId = ASSET_B, fileHash = HASH_B, status = "READY")

        val active = mediaRepository.findActiveByWorkspaceAndHash(workspaceId, HASH_A)

        active.shouldNotBeNull()
        active.assetId shouldBe ASSET_A
        active.status shouldBe MediaAssetStatus.READY
    }

    @Test
    fun `findActiveByWorkspaceAndHash ignores DELETED and FAILED rows`() = runTest {
        val workspaceId = nextWorkspaceId()
        seedWorkspace(workspaceId)
        seedBlob(workspaceId, HASH_A)
        // Only DELETED + FAILED rows reference HASH_A — the lookup must return null so a
        // re-upload can proceed without colliding with soft-deleted history.
        insertAsset(workspaceId = workspaceId, assetId = ASSET_DELETED, fileHash = HASH_A, status = "DELETED")
        insertAsset(workspaceId = workspaceId, assetId = ASSET_FAILED, fileHash = HASH_A, status = "FAILED")

        val active = mediaRepository.findActiveByWorkspaceAndHash(workspaceId, HASH_A)

        active.shouldBeNull()
    }

    @Test
    fun `licence maps correctly through create and findByWorkspaceAndId`() = runTest {
        val workspaceId = nextWorkspaceId()
        seedWorkspace(workspaceId)
        seedBlob(workspaceId, HASH_A)

        val created = mediaRepository.create(
            MediaAsset(
                assetId = ASSET_LICENCE,
                workspaceId = workspaceId,
                sourceType = MediaSourceType.EXTERNAL,
                fileHash = HASH_A,
                mediaType = "image/jpeg",
                storageKey = "assets/key.jpg",
                detectedMediaType = "image/jpeg",
                originalFilename = "photo.jpg",
                fileSizeBytes = 1024L,
                status = MediaAssetStatus.READY,
                createdAt = Instant.now(),
                sourceProvider = "unsplash",
                externalId = "photo-1",
                sourceUrl = "https://unsplash.com/photos/photo-1",
                authorName = "Test Author",
                authorUrl = "https://unsplash.com/@test-author",
                metadata = null,
                licence = "unsplash",
            ),
        )

        val read = mediaRepository.findByWorkspaceAndId(workspaceId, ASSET_LICENCE)

        read.shouldNotBeNull()
        created.licence shouldBe "unsplash"
        read.licence shouldBe "unsplash"
    }

    private suspend fun insertAsset(workspaceId: String, assetId: String, fileHash: String, status: String) {
        databaseClient.sql(
            """
            INSERT INTO media_assets (
                asset_id, workspace_id, source_type, file_hash, media_type, storage_key,
                original_filename, file_size_bytes, status, created_at
            ) VALUES (
                :assetId, :workspaceId, 'UPLOADED', :fileHash, 'image/png',
                CASE WHEN :status = 'READY' THEN 'assets/key.png' ELSE NULL END,
                'photo.png', 1024, :status, :createdAt
            )
            """.trimIndent(),
        )
            .bind("assetId", UUID.fromString(assetId))
            .bind("workspaceId", UUID.fromString(workspaceId))
            .bind("fileHash", fileHash)
            .bind("status", status)
            .bind("createdAt", Instant.now().atOffset(java.time.ZoneOffset.UTC))
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    companion object {
        private const val ASSET_A = "ac4cf984-8ab6-569f-8cb8-93bea5654e34"
        private const val ASSET_B = "3c1154b6-1e18-5724-b57b-4c32641d035e"
        private const val ASSET_DELETED = "9797fdda-42f6-59d2-9860-da5e4ed69053"
        private const val ASSET_FAILED = "551222eb-6564-50a8-a5c4-b500dd308771"
        private const val ASSET_LICENCE = "6ff9a8b3-58d1-5c5c-bdd1-391ab77de04e"

        private const val HASH_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        private const val HASH_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

        @Container
        @JvmStatic
        val postgresContainer = PostgresTestContainerSupport.newContainer("media_asset_repository")
    }
}
