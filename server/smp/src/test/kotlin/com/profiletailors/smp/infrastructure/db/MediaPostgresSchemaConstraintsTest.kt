package com.profiletailors.smp.infrastructure.db

import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MediaPostgresSchemaConstraintsTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    @Test
    fun `workspace file blobs expose PostgreSQL partial garbage collection index`() = runTest {
        val indexDefinition = databaseClient.sql(
            """
            SELECT indexdef
            FROM pg_indexes
            WHERE tablename = 'workspace_file_blobs'
              AND indexname = 'idx_blobs_gc_candidates'
            """.trimIndent(),
        )
            .map { row, _ -> requireNotNull(row.get("indexdef", String::class.java)) }
            .one()
            .awaitSingle()

        assertTrue(indexDefinition.contains("WHERE", ignoreCase = true), indexDefinition)
        assertTrue(indexDefinition.contains("READY_FOR_GC"), indexDefinition)
    }

    @Test
    fun `composite foreign key rejects media assets without matching workspace blob`() = runTest {
        seedWorkspace("workspace-1")

        assertThrows<RuntimeException> {
            runTest {
                insertMediaAsset(assetId = "asset-invalid", workspaceId = "workspace-1", fileHash = HASH_A)
            }
        }
    }

    @Test
    fun `status and hash constraints reject invalid rows while accepting valid rows`() = runTest {
        seedWorkspace("workspace-1")
        insertWorkspaceBlob(workspaceId = "workspace-1", fileHash = HASH_A, status = "READY")

        insertMediaAsset(assetId = "asset-valid", workspaceId = "workspace-1", fileHash = HASH_A)
        val validCount = countRows("media_assets")
        assertEquals(1, validCount)

        assertThrows<RuntimeException> {
            runTest { insertWorkspaceBlob(workspaceId = "workspace-1", fileHash = HASH_B, status = "BOGUS") }
        }
        assertThrows<RuntimeException> {
            runTest { insertWorkspaceBlob(workspaceId = "workspace-1", fileHash = "short", status = "READY") }
        }
        assertThrows<RuntimeException> {
            runTest {
                insertMediaAsset(
                    assetId = "asset-bogus-status",
                    workspaceId = "workspace-1",
                    fileHash = HASH_A,
                    status = "PROCESSING",
                )
            }
        }
    }

    @Test
    fun `media assets storage key is nullable before ready and required only for ready`() = runTest {
        seedWorkspace("workspace-1")
        insertWorkspaceBlob(workspaceId = "workspace-1", fileHash = HASH_A, status = "UPLOADING", storageKey = null)

        insertMediaAsset(
            assetId = "asset-pending",
            workspaceId = "workspace-1",
            fileHash = HASH_A,
            status = "PENDING_UPLOAD",
            storageKey = null,
        )
        assertEquals(1, countRows("media_assets"))

        assertThrows<RuntimeException> {
            runTest {
                insertMediaAsset(
                    assetId = "asset-pending-with-key",
                    workspaceId = "workspace-1",
                    fileHash = HASH_A,
                    status = "PENDING_UPLOAD",
                    storageKey = "storage/key.png",
                )
            }
        }
        assertThrows<RuntimeException> {
            runTest {
                insertMediaAsset(
                    assetId = "asset-ready-without-key",
                    workspaceId = "workspace-1",
                    fileHash = HASH_A,
                    status = "READY",
                    storageKey = null,
                )
            }
        }
    }

    @Test
    fun `workspace file blobs require canonical metadata when ready`() = runTest {
        seedWorkspace("workspace-1")

        insertWorkspaceBlob(workspaceId = "workspace-1", fileHash = HASH_A, status = "UPLOADING", storageKey = null)

        assertThrows<RuntimeException> {
            runTest {
                insertWorkspaceBlob(workspaceId = "workspace-1", fileHash = HASH_B, status = "READY", storageKey = null)
            }
        }
    }

    @Test
    fun `schema exposes composite relationship between media assets and workspace file blobs`() = runTest {
        val constraint = databaseClient.sql(
            """
            SELECT conname
            FROM pg_constraint
            WHERE conname = 'fk_media_assets_workspace_file_blob'
              AND conrelid = 'media_assets'::regclass
            """.trimIndent(),
        )
            .map { row, _ -> requireNotNull(row.get("conname", String::class.java)) }
            .one()
            .awaitSingle()

        assertNotNull(constraint)
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
        storageKey: String? = "storage/key",
        detectedMediaType: String? = "image/png",
        fileSizeBytes: Long? = 1024,
    ) {
        var spec = databaseClient.sql(
            """
            INSERT INTO workspace_file_blobs (
                workspace_id, file_hash, storage_key, detected_media_type, file_size_bytes, status
            ) VALUES (
                :workspaceId, :fileHash, :storageKey, :detectedMediaType, :fileSizeBytes, :status
            )
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .bind("fileHash", fileHash)
            .bind("status", status)
        spec =
            if (storageKey ==
                null
            ) {
                spec.bindNull("storageKey", String::class.java)
            } else {
                spec.bind("storageKey", storageKey)
            }
        spec =
            if (detectedMediaType ==
                null
            ) {
                spec.bindNull("detectedMediaType", String::class.java)
            } else {
                spec.bind("detectedMediaType", detectedMediaType)
            }
        spec =
            if (fileSizeBytes ==
                null
            ) {
                spec.bindNull("fileSizeBytes", java.lang.Long::class.java)
            } else {
                spec.bind("fileSizeBytes", fileSizeBytes)
            }
        spec.fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun insertMediaAsset(
        assetId: String,
        workspaceId: String,
        fileHash: String,
        status: String = "READY",
        storageKey: String? = "storage/key.png",
    ) {
        var spec = databaseClient.sql(
            """
            INSERT INTO media_assets (
                asset_id, workspace_id, source_type, file_hash, media_type, storage_key,
                original_filename, file_size_bytes, status, created_at
            ) VALUES (
                :assetId, :workspaceId, 'UPLOADED', :fileHash, 'image/png', :storageKey,
                'asset.png', 1024, :status, CURRENT_TIMESTAMP
            )
            """.trimIndent(),
        )
            .bind("assetId", assetId)
            .bind("workspaceId", workspaceId)
            .bind("fileHash", fileHash)
            .bind("status", status)
        spec =
            if (storageKey ==
                null
            ) {
                spec.bindNull("storageKey", String::class.java)
            } else {
                spec.bind("storageKey", storageKey)
            }
        spec.fetch().rowsUpdated().awaitSingle()
    }

    /**
     * Count rows in a table. `tableName` is validated to contain only alphanumeric
     * characters and underscores to prevent SQL injection. Callers pass compile-time
     * constants (e.g. `"media_assets"`), not user input.
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
        private const val HASH_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

        @Container
        @JvmStatic
        val postgresContainer = PostgresTestContainerSupport.newContainer("media_schema_constraints")
    }
}
