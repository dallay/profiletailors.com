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
    fun `legacy processing media asset may reserve storage key without file hash`() = runTest {
        seedWorkspace("workspace-legacy")

        insertMediaAsset(
            assetId = "asset-legacy-processing",
            workspaceId = "workspace-legacy",
            fileHash = null,
            status = "PROCESSING",
            storageKey = "workspace-legacy/assets/asset-legacy-processing",
        )

        assertEquals(1, countRows("media_assets"))
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

    @Test
    fun `media assets expose partial UNIQUE index on workspace and hash for active rows`() = runTest {
        val indexDefinition = databaseClient.sql(
            """
            SELECT indexdef
            FROM pg_indexes
            WHERE tablename = 'media_assets'
              AND indexname = 'uq_media_assets_active_per_hash'
            """.trimIndent(),
        )
            .map { row, _ -> requireNotNull(row.get("indexdef", String::class.java)) }
            .one()
            .awaitSingle()

        assertTrue(indexDefinition.contains("UNIQUE", ignoreCase = true), indexDefinition)
        assertTrue(indexDefinition.contains("workspace_id", ignoreCase = true), indexDefinition)
        assertTrue(indexDefinition.contains("file_hash", ignoreCase = true), indexDefinition)
        assertTrue(indexDefinition.contains("WHERE", ignoreCase = true), indexDefinition)
        assertTrue(indexDefinition.contains("DELETED", ignoreCase = true), indexDefinition)
        assertTrue(indexDefinition.contains("FAILED", ignoreCase = true), indexDefinition)
    }

    @Test
    fun `partial UNIQUE on media assets rejects duplicate active rows but allows soft-deleted`() = runTest {
        seedWorkspace("workspace-dup")
        insertWorkspaceBlob(workspaceId = "workspace-dup", fileHash = HASH_A, status = "READY")
        insertMediaAsset(assetId = "asset-dup-1", workspaceId = "workspace-dup", fileHash = HASH_A, status = "READY")

        // Second ACTIVE row with the same hash in the same workspace must be rejected.
        assertThrows<RuntimeException> {
            runTest {
                insertMediaAsset(
                    assetId = "asset-dup-2",
                    workspaceId = "workspace-dup",
                    fileHash = HASH_A,
                    status = "READY",
                )
            }
        }

        // DELETED + retry must be allowed: soft-deleted rows do not occupy the partial index.
        insertMediaAsset(
            assetId = "asset-dup-3",
            workspaceId = "workspace-dup",
            fileHash = HASH_A,
            status = "DELETED",
            storageKey = null,
        )
        insertMediaAsset(
            assetId = "asset-dup-4",
            workspaceId = "workspace-dup",
            fileHash = HASH_A,
            status = "FAILED",
            storageKey = null,
        )
        insertMediaAsset(
            assetId = "asset-dup-5",
            workspaceId = "workspace-dup",
            fileHash = HASH_A,
            status = "FAILED",
            storageKey = null,
        )

        assertEquals(4, countRows("media_assets"))
    }

    @Test
    fun `media assets expose external metadata columns`() = runTest {
        val columns = databaseClient.sql(
            """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_name = 'media_assets'
              AND column_name IN (
                'source_provider', 'external_id', 'source_url',
                'author_name', 'author_url', 'metadata'
              )
            """.trimIndent(),
        )
            .map { row, _ -> requireNotNull(row.get("column_name", String::class.java)) }
            .all()
            .collectList()
            .awaitSingle()
            .toSet()

        assertEquals(
            setOf("source_provider", "external_id", "source_url", "author_name", "author_url", "metadata"),
            columns,
        )
    }

    @Test
    fun `media assets reject uploaded rows with a source provider`() = runTest {
        seedWorkspace("workspace-external-check")
        insertWorkspaceBlob(workspaceId = "workspace-external-check", fileHash = HASH_A, status = "READY")

        val exception = kotlin.runCatching {
            insertMediaAsset(
                assetId = "asset-uploaded-provider",
                workspaceId = "workspace-external-check",
                fileHash = HASH_A,
                sourceProvider = "unsplash",
            )
        }.exceptionOrNull()

        assertNotNull(exception)
        val stack = exception!!.stackTraceToString()
        assertTrue(
            stack.contains("chk_asset_uploaded_implies_no_provider") ||
                stack.contains("chk_asset_uploaded_implies_no_external_attribution"),
            stack,
        )
    }

    @Test
    fun `media assets reject external rows without provider or id`() = runTest {
        seedWorkspace("workspace-external-checks")
        insertWorkspaceBlob(workspaceId = "workspace-external-checks", fileHash = HASH_A, status = "READY")

        val missingProvider = kotlin.runCatching {
            insertMediaAsset(
                assetId = "asset-external-missing-provider",
                workspaceId = "workspace-external-checks",
                fileHash = HASH_A,
                sourceType = "EXTERNAL",
                sourceProvider = null,
                externalId = "photo-123",
            )
        }.exceptionOrNull()
        val missingId = kotlin.runCatching {
            insertMediaAsset(
                assetId = "asset-external-missing-id",
                workspaceId = "workspace-external-checks",
                fileHash = HASH_A,
                sourceType = "EXTERNAL",
                sourceProvider = "unsplash",
                externalId = null,
            )
        }.exceptionOrNull()

        assertNotNull(missingProvider)
        assertNotNull(missingId)
        assertTrue(
            missingProvider!!.stackTraceToString().contains("chk_asset_external_implies_provider_and_id"),
            missingProvider.stackTraceToString(),
        )
        assertTrue(
            missingId!!.stackTraceToString().contains("chk_asset_external_implies_provider_and_id"),
            missingId.stackTraceToString(),
        )
    }

    @Test
    fun `media assets reject external rows without source url`() = runTest {
        seedWorkspace("workspace-external-url-check")
        insertWorkspaceBlob(workspaceId = "workspace-external-url-check", fileHash = HASH_A, status = "READY")

        val exception = kotlin.runCatching {
            insertMediaAsset(
                assetId = "asset-external-missing-url",
                workspaceId = "workspace-external-url-check",
                fileHash = HASH_A,
                sourceType = "EXTERNAL",
                sourceProvider = "unsplash",
                externalId = "photo-123",
                sourceUrl = null,
            )
        }.exceptionOrNull()

        assertNotNull(exception)
        assertTrue(
            exception!!.stackTraceToString().contains("chk_asset_external_implies_provider_and_id"),
            exception.stackTraceToString(),
        )
    }

    @Test
    fun `media assets reject uploaded rows that carry external attribution`() = runTest {
        seedWorkspace("workspace-uploaded-no-attribution")
        insertWorkspaceBlob(workspaceId = "workspace-uploaded-no-attribution", fileHash = HASH_A, status = "READY")

        val authorName = kotlin.runCatching {
            insertMediaAsset(
                assetId = "asset-uploaded-with-author-name",
                workspaceId = "workspace-uploaded-no-attribution",
                fileHash = HASH_A,
                sourceType = "UPLOADED",
                authorName = "Jane Creator",
            )
        }.exceptionOrNull()
        val authorUrl = kotlin.runCatching {
            insertMediaAsset(
                assetId = "asset-uploaded-with-author-url",
                workspaceId = "workspace-uploaded-no-attribution",
                fileHash = HASH_A,
                sourceType = "UPLOADED",
                authorUrl = "https://example.com/@jane",
            )
        }.exceptionOrNull()

        assertNotNull(authorName)
        assertNotNull(authorUrl)
        assertTrue(
            authorName!!.stackTraceToString()
                .contains("chk_asset_uploaded_implies_no_external_attribution"),
            authorName.stackTraceToString(),
        )
        assertTrue(
            authorUrl!!.stackTraceToString()
                .contains("chk_asset_uploaded_implies_no_external_attribution"),
            authorUrl.stackTraceToString(),
        )
    }

    @Test
    fun `media assets accept external rows with full attribution including url`() = runTest {
        seedWorkspace("workspace-external-accept")
        insertWorkspaceBlob(workspaceId = "workspace-external-accept", fileHash = HASH_A, status = "READY")

        insertMediaAsset(
            assetId = "asset-external-full",
            workspaceId = "workspace-external-accept",
            fileHash = HASH_A,
            sourceType = "EXTERNAL",
            sourceProvider = "unsplash",
            externalId = "photo-123",
            sourceUrl = "https://unsplash.com/photos/photo-123",
            authorName = "Jane Creator",
            authorUrl = "https://unsplash.com/@jane",
        )

        assertEquals(1, countRows("media_assets"))
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
                spec.bindNull("fileSizeBytes", Long::class.java)
            } else {
                spec.bind("fileSizeBytes", fileSizeBytes)
            }
        spec.fetch().rowsUpdated().awaitSingle()
    }

    private suspend fun insertMediaAsset(
        assetId: String,
        workspaceId: String,
        fileHash: String?,
        status: String = "READY",
        storageKey: String? = "storage/key.png",
        sourceType: String = "UPLOADED",
        sourceProvider: String? = null,
        externalId: String? = null,
        sourceUrl: String? = null,
        authorName: String? = null,
        authorUrl: String? = null,
    ) {
        var spec = databaseClient.sql(
            """
            INSERT INTO media_assets (
                asset_id, workspace_id, source_type, file_hash, media_type, storage_key,
                original_filename, file_size_bytes, status, created_at,
                source_provider, external_id, source_url, author_name, author_url
            ) VALUES (
                :assetId, :workspaceId, :sourceType, :fileHash, 'image/png', :storageKey,
                'asset.png', 1024, :status, CURRENT_TIMESTAMP,
                :sourceProvider, :externalId, :sourceUrl, :authorName, :authorUrl
            )
            """.trimIndent(),
        )
            .bind("assetId", assetId)
            .bind("workspaceId", workspaceId)
            .bind("sourceType", sourceType)
            .bind("status", status)
        spec = if (fileHash == null) {
            spec.bindNull("fileHash", String::class.java)
        } else {
            spec.bind("fileHash", fileHash)
        }
        spec =
            if (storageKey ==
                null
            ) {
                spec.bindNull("storageKey", String::class.java)
            } else {
                spec.bind("storageKey", storageKey)
            }
        spec = if (sourceProvider == null) {
            spec.bindNull("sourceProvider", String::class.java)
        } else {
            spec.bind("sourceProvider", sourceProvider)
        }
        spec = if (externalId == null) {
            spec.bindNull("externalId", String::class.java)
        } else {
            spec.bind("externalId", externalId)
        }
        spec = if (sourceUrl == null) {
            spec.bindNull("sourceUrl", String::class.java)
        } else {
            spec.bind("sourceUrl", sourceUrl)
        }
        spec = if (authorName == null) {
            spec.bindNull("authorName", String::class.java)
        } else {
            spec.bind("authorName", authorName)
        }
        spec = if (authorUrl == null) {
            spec.bindNull("authorUrl", String::class.java)
        } else {
            spec.bind("authorUrl", authorUrl)
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
            .map { row, _ -> row.get("cnt", Long::class.java)?.toInt() ?: 0 }
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
