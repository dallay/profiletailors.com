package com.profiletailors.smp.media.infrastructure.persistence

import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.media.domain.MediaAssetStatus
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcMediaRepositoriesPostgresTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    private val blobRepository by lazy { R2dbcWorkspaceFileBlobRepository(databaseClient) }
    private val mediaRepository by lazy { R2dbcMediaAssetRepository(databaseClient) }

    @Test
    fun `native UUID assets support collection lookup and cursor pagination`() = runTest {
        seedWorkspace(WORKSPACE_1)
        insertWorkspaceBlob(WORKSPACE_1, HASH_A, "READY")
        insertMediaAsset(ASSET_1, WORKSPACE_1, HASH_A, "DELETED")
        insertMediaAsset(ASSET_A, WORKSPACE_1, HASH_A, "DELETED")

        val loaded = mediaRepository.findByWorkspaceAndIds(WORKSPACE_1, listOf(ASSET_1, ASSET_A))
        val first = mediaRepository.listByWorkspace(WORKSPACE_1, setOf(MediaAssetStatus.DELETED), 1, null)
        val second = mediaRepository.listByWorkspace(
            WORKSPACE_1,
            setOf(MediaAssetStatus.DELETED),
            1,
            requireNotNull(first.nextCursor),
        )

        assertEquals(setOf(ASSET_1, ASSET_A), loaded.map { it.assetId }.toSet())
        assertEquals(setOf(ASSET_1, ASSET_A), (first.assets + second.assets).map { it.assetId }.toSet())
        assertNull(second.nextCursor)
    }

    @Test
    fun `native UUID workspace supports upload slots and hourly limits`() = runTest {
        seedWorkspace(WORKSPACE_1)
        val rateLimits = R2dbcMediaRateLimitRepository(databaseClient)

        assertTrue(rateLimits.tryClaimConcurrentUploadSlot(WORKSPACE_1, 1))
        assertTrue(!rateLimits.tryClaimConcurrentUploadSlot(WORKSPACE_1, 1))
        rateLimits.releaseConcurrentUploadSlot(WORKSPACE_1)
        assertTrue(rateLimits.tryClaimConcurrentUploadSlot(WORKSPACE_1, 1))
        assertTrue(rateLimits.tryIncrementHourlyCreationCount(WORKSPACE_1, 1).isAllowed)
        assertTrue(!rateLimits.tryIncrementHourlyCreationCount(WORKSPACE_1, 1).isAllowed)
    }

    @Test
    fun `blob upsert uses PostgreSQL ON CONFLICT without duplicating rows`() = runTest {
        seedWorkspace(WORKSPACE_1)

        blobRepository.upsertBlob(WORKSPACE_1, HASH_A)
        blobRepository.upsertBlob(WORKSPACE_1, HASH_A)

        assertEquals(1, countRows("workspace_file_blobs"))
    }

    @Test
    fun `CAS upload claim only transitions pending assets once`() = runTest {
        seedWorkspace(WORKSPACE_1)
        insertWorkspaceBlob(WORKSPACE_1, HASH_A, "UPLOADING")
        insertMediaAsset(ASSET_1, WORKSPACE_1, HASH_A, "PENDING_UPLOAD")

        val firstClaim = mediaRepository.claimCasUploadSlot(
            ASSET_1,
            WORKSPACE_1,
            Instant.parse("2026-06-25T10:00:00Z"),
        )
        val secondClaim = mediaRepository.claimCasUploadSlot(
            ASSET_1,
            WORKSPACE_1,
            Instant.parse("2026-06-25T10:01:00Z"),
        )

        assertTrue(firstClaim)
        assertTrue(!secondClaim)
    }

    @Test
    fun `findBlobForUpdate and findReadyForGC execute PostgreSQL lock clauses`() = runTest {
        seedWorkspace(WORKSPACE_1)
        insertWorkspaceBlob(WORKSPACE_1, HASH_A, "READY_FOR_GC", orphanedAt = Instant.parse("2026-06-01T00:00:00Z"))

        val locked = blobRepository.findBlobForUpdate(WORKSPACE_1, HASH_A)
        val candidates = blobRepository.findReadyForGC(Instant.parse("2026-06-10T00:00:00Z"), 10).toList()

        assertNotNull(locked)
        assertEquals(listOf(HASH_A), candidates.map { it.fileHash })
    }

    @Test
    fun `cleanup removes media assets before workspace file blobs between tests`() = runTest {
        assertEquals(0, countRows("media_assets"))
        assertEquals(0, countRows("workspace_file_blobs"))
        assertNull(blobRepository.findByWorkspaceAndHash(WORKSPACE_1, HASH_A))
    }

    @Test
    fun `findActiveByWorkspaceAndHash returns the active asset that references the hash`() = runTest {
        seedWorkspace(WORKSPACE_1)
        insertWorkspaceBlob(WORKSPACE_1, HASH_A, "READY")
        insertMediaAsset(ASSET_A, WORKSPACE_1, HASH_A, "READY")

        val active = mediaRepository.findActiveByWorkspaceAndHash(WORKSPACE_1, HASH_A)

        assertEquals(ASSET_A, active?.assetId)
    }

    @Test
    fun `findActiveByWorkspaceAndHash ignores DELETED and FAILED rows`() = runTest {
        seedWorkspace(WORKSPACE_1)
        insertWorkspaceBlob(WORKSPACE_1, HASH_A, "READY")
        // Only DELETED + FAILED rows reference HASH_A — neither should match.
        insertMediaAsset(ASSET_DELETED, WORKSPACE_1, HASH_A, "DELETED")
        insertMediaAsset(ASSET_FAILED, WORKSPACE_1, HASH_A, "FAILED")

        val active = mediaRepository.findActiveByWorkspaceAndHash(WORKSPACE_1, HASH_A)

        assertNull(active, "DELETED/FAILED must not block re-upload of the same hash")
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

    private suspend fun insertWorkspaceBlob(
        workspaceId: String,
        fileHash: String,
        status: String,
        orphanedAt: Instant? = null,
    ) {
        databaseClient.sql(
            """
            INSERT INTO workspace_file_blobs (
                workspace_id, file_hash, storage_key, file_size_bytes, detected_media_type, status, orphaned_at
            ) VALUES (
                :workspaceId, :fileHash, 'assets/key.png', 1024, 'image/png', :status, :orphanedAt
            )
            """.trimIndent(),
        )
            .bind("workspaceId", UUID.fromString(workspaceId))
            .bind("fileHash", fileHash)
            .bind("status", status)
            .let { spec ->
                if (orphanedAt == null) {
                    spec.bindNull("orphanedAt", java.time.OffsetDateTime::class.java)
                } else {
                    spec.bind("orphanedAt", java.time.OffsetDateTime.ofInstant(orphanedAt, java.time.ZoneOffset.UTC))
                }
            }
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun insertMediaAsset(assetId: String, workspaceId: String, fileHash: String, status: String) {
        databaseClient.sql(
            """
            INSERT INTO media_assets (
                asset_id, workspace_id, source_type, file_hash, media_type, storage_key,
                original_filename, file_size_bytes, status, created_at
            ) VALUES (
                :assetId, :workspaceId, 'UPLOADED', :fileHash, 'image/png',
                CASE WHEN :status = 'READY' THEN 'assets/key.png' ELSE NULL END,
                'asset.png', 1024, :status, CURRENT_TIMESTAMP
            )
            """.trimIndent(),
        )
            .bind("assetId", UUID.fromString(assetId))
            .bind("workspaceId", UUID.fromString(workspaceId))
            .bind("fileHash", fileHash)
            .bind("status", status)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    /**
     * Count rows in a table. Parameterized via `:tableName` placeholder.
     * `tableName` is validated to contain only alphanumeric characters and underscores
     * to prevent SQL injection.
     */
    private suspend fun countRows(tableName: String): Int {
        require(tableName.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*$"))) {
            "countRows: invalid table name — must be alphanumeric with optional underscores: $tableName"
        }
        return databaseClient.sql("SELECT COUNT(*) AS cnt FROM $tableName")
            .map { row, _ -> (row.get("cnt") as Number).toInt() }
            .one()
            .awaitSingle()
    }

    companion object {
        private const val WORKSPACE_1 = "d3d9800f-120a-501e-ad01-401e54e8f766"
        private const val ASSET_1 = "484cbbea-47d9-508d-83e1-b80932bcd6ab"
        private const val ASSET_A = "ac4cf984-8ab6-569f-8cb8-93bea5654e34"
        private const val ASSET_DELETED = "9797fdda-42f6-59d2-9860-da5e4ed69053"
        private const val ASSET_FAILED = "551222eb-6564-50a8-a5c4-b500dd308771"

        private const val HASH_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

        @Container
        @JvmStatic
        val postgresContainer = PostgresTestContainerSupport.newContainer("media_repository_postgres")
    }
}
