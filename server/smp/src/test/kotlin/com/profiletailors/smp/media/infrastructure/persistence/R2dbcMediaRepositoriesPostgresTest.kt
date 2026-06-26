package com.profiletailors.smp.media.infrastructure.persistence

import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.media.domain.BlobStatus
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

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcMediaRepositoriesPostgresTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    private val blobRepository by lazy { R2dbcWorkspaceFileBlobRepository(databaseClient) }
    private val mediaRepository by lazy { R2dbcMediaAssetRepository(databaseClient) }

    @Test
    fun `blob upsert uses PostgreSQL ON CONFLICT without duplicating rows`() = runTest {
        seedWorkspace("workspace-1")

        blobRepository.upsertBlob("workspace-1", HASH_A)
        blobRepository.upsertBlob("workspace-1", HASH_A)

        assertEquals(1, countRows("workspace_file_blobs"))
    }

    @Test
    fun `CAS upload claim only transitions pending assets once`() = runTest {
        seedWorkspace("workspace-1")
        insertWorkspaceBlob("workspace-1", HASH_A, "UPLOADING")
        insertMediaAsset("asset-1", "workspace-1", HASH_A, "PENDING_UPLOAD")

        val firstClaim = mediaRepository.claimCasUploadSlot("asset-1", "workspace-1", Instant.parse("2026-06-25T10:00:00Z"))
        val secondClaim = mediaRepository.claimCasUploadSlot("asset-1", "workspace-1", Instant.parse("2026-06-25T10:01:00Z"))

        assertTrue(firstClaim)
        assertTrue(!secondClaim)
    }

    @Test
    fun `findBlobForUpdate and findReadyForGC execute PostgreSQL lock clauses`() = runTest {
        seedWorkspace("workspace-1")
        insertWorkspaceBlob("workspace-1", HASH_A, "READY_FOR_GC", orphanedAt = Instant.parse("2026-06-01T00:00:00Z"))

        val locked = blobRepository.findBlobForUpdate("workspace-1", HASH_A)
        val candidates = blobRepository.findReadyForGC(Instant.parse("2026-06-10T00:00:00Z"), 10).toList()

        assertNotNull(locked)
        assertEquals(listOf(HASH_A), candidates.map { it.fileHash })
    }

    @Test
    fun `cleanup removes media assets before workspace file blobs between tests`() = runTest {
        assertEquals(0, countRows("media_assets"))
        assertEquals(0, countRows("workspace_file_blobs"))
        assertNull(blobRepository.findByWorkspaceAndHash("workspace-1", HASH_A))
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
            .bind("workspaceId", workspaceId)
            .bind("fileHash", fileHash)
            .bind("status", status)
            .let { spec ->
                if (orphanedAt == null) spec.bindNull("orphanedAt", java.time.OffsetDateTime::class.java)
                else spec.bind("orphanedAt", java.time.OffsetDateTime.ofInstant(orphanedAt, java.time.ZoneOffset.UTC))
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
            .bind("assetId", assetId)
            .bind("workspaceId", workspaceId)
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
        private const val HASH_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

        @Container
        @JvmStatic
        val postgresContainer = PostgresTestContainerSupport.newContainer("media_repository_postgres")
    }
}
