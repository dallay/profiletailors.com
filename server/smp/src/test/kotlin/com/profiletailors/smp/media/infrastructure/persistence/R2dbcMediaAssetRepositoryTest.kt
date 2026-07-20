package com.profiletailors.smp.media.infrastructure.persistence

import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.media.domain.MediaAsset
import com.profiletailors.smp.media.domain.MediaAssetStatus
import com.profiletailors.smp.media.domain.MediaSourceType
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

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
    private var workspaceCounter = 0

    /**
     * Use unique workspace ids for each seed set so assertions can target exactly
     * the rows created by the current test.
     */
    private fun nextWorkspaceId(): String {
        workspaceCounter += 1
        return "ws-test-$workspaceCounter"
    }

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
            .bind("workspaceId", workspaceId)
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
            .bind("workspaceId", workspaceId)
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
        insertAsset(workspaceId = workspaceId, assetId = "asset-a", fileHash = HASH_A, status = "READY")
        insertAsset(workspaceId = workspaceId, assetId = "asset-b", fileHash = HASH_B, status = "READY")

        val active = mediaRepository.findActiveByWorkspaceAndHash(workspaceId, HASH_A)

        assertNotNull(active)
        assertEquals("asset-a", active!!.assetId, "oldest active asset by created_at wins")
        assertEquals(MediaAssetStatus.READY, active.status)
    }

    @Test
    fun `findActiveByWorkspaceAndHash ignores DELETED and FAILED rows`() = runTest {
        val workspaceId = nextWorkspaceId()
        seedWorkspace(workspaceId)
        seedBlob(workspaceId, HASH_A)
        // Only DELETED + FAILED rows reference HASH_A — the lookup must return null so a
        // re-upload can proceed without colliding with soft-deleted history.
        insertAsset(workspaceId = workspaceId, assetId = "asset-deleted", fileHash = HASH_A, status = "DELETED")
        insertAsset(workspaceId = workspaceId, assetId = "asset-failed", fileHash = HASH_A, status = "FAILED")

        val active = mediaRepository.findActiveByWorkspaceAndHash(workspaceId, HASH_A)

        assertNull(active, "DELETED/FAILED must not block re-upload of the same hash")
    }

    @Test
    fun `licence maps correctly through create and findByWorkspaceAndId`() = runTest {
        val workspaceId = nextWorkspaceId()
        seedWorkspace(workspaceId)
        seedBlob(workspaceId, HASH_A)

        val created = mediaRepository.create(
            MediaAsset(
                assetId = "asset-licence",
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

        val read = mediaRepository.findByWorkspaceAndId(workspaceId, "asset-licence")

        assertNotNull(read)
        assertEquals("unsplash", created.licence)
        assertEquals("unsplash", read!!.licence)
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
            .bind("assetId", assetId)
            .bind("workspaceId", workspaceId)
            .bind("fileHash", fileHash)
            .bind("status", status)
            .bind("createdAt", Instant.now().atOffset(java.time.ZoneOffset.UTC))
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    companion object {
        private const val HASH_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        private const val HASH_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

        @Container
        @JvmStatic
        val postgresContainer = PostgresTestContainerSupport.newContainer("media_asset_repository")
    }
}
