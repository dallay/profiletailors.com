package com.profiletailors.smp.media.infrastructure.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import com.profiletailors.smp.media.domain.MediaAsset
import com.profiletailors.smp.media.domain.MediaAssetStatus
import com.profiletailors.smp.media.domain.MediaSourceType
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
class R2dbcMediaAssetRepositoryExternalMetadataTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    private lateinit var repository: R2dbcMediaAssetRepository

    @BeforeEach
    fun setUp() {
        repository = R2dbcMediaAssetRepository(databaseClient, ObjectMapper())
    }

    @Test
    fun `external metadata round trips through repository`() = runTest {
        seedWorkspace("workspace-1")
        insertWorkspaceBlob("workspace-1", HASH)
        val asset = externalAsset(
            metadata = mapOf(
                "provider" to mapOf("downloadLocation" to "https://api.unsplash.com/photos/photo-123/download"),
                "palette" to listOf("#000000", "#ffffff"),
            ),
        )

        repository.create(asset)

        val found = requireNotNull(repository.findByWorkspaceAndId("workspace-1", "asset-external"))
        assertEquals(MediaSourceType.EXTERNAL, found.sourceType)
        assertEquals("unsplash", found.sourceProvider)
        assertEquals("photo-123", found.externalId)
        assertEquals("https://unsplash.com/photos/photo-123", found.sourceUrl)
        assertEquals("Jane Creator", found.authorName)
        assertEquals("https://unsplash.com/@jane", found.authorUrl)
        assertEquals(listOf("#000000", "#ffffff"), found.metadata?.get("palette"))
    }

    @Test
    fun `database rejects uploaded rows with source provider`() = runTest {
        seedWorkspace("workspace-2")
        insertWorkspaceBlob("workspace-2", HASH)

        val exception = kotlin.runCatching {
            insertRawAsset(
                assetId = "asset-uploaded-provider",
                workspaceId = "workspace-2",
                sourceType = "UPLOADED",
                sourceProvider = "unsplash",
                externalId = null,
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
    fun `database rejects external rows without provider`() = runTest {
        seedWorkspace("workspace-3")
        insertWorkspaceBlob("workspace-3", HASH)

        val exception = kotlin.runCatching {
            insertRawAsset(
                assetId = "asset-missing-provider",
                workspaceId = "workspace-3",
                sourceType = "EXTERNAL",
                sourceProvider = null,
                externalId = "photo-123",
            )
        }.exceptionOrNull()

        assertNotNull(exception)
        assertConstraintViolation(exception, "chk_asset_external_implies_provider_and_id")
    }

    @Test
    fun `database rejects external rows without external id`() = runTest {
        seedWorkspace("workspace-4")
        insertWorkspaceBlob("workspace-4", HASH)

        val exception = kotlin.runCatching {
            insertRawAsset(
                assetId = "asset-missing-id",
                workspaceId = "workspace-4",
                sourceType = "EXTERNAL",
                sourceProvider = "unsplash",
                externalId = null,
            )
        }.exceptionOrNull()

        assertNotNull(exception)
        assertConstraintViolation(exception, "chk_asset_external_implies_provider_and_id")
    }

    @Test
    fun `metadata jsonb supports nested structure`() = runTest {
        seedWorkspace("workspace-5")
        insertWorkspaceBlob("workspace-5", HASH)
        repository.create(
            externalAsset(
                assetId = "asset-jsonb",
                workspaceId = "workspace-5",
                metadata = mapOf("nested" to mapOf("width" to 1080, "tags" to listOf("social", "hero"))),
            ),
        )

        val value = databaseClient.sql(
            """
            SELECT metadata #>> '{nested,tags,1}' AS tag
            FROM media_assets
            WHERE asset_id = 'asset-jsonb'
            """.trimIndent(),
        )
            .map { row, _ -> requireNotNull(row.get("tag", String::class.java)) }
            .one()
            .awaitSingle()

        assertEquals("hero", value)
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

    private suspend fun insertWorkspaceBlob(workspaceId: String, fileHash: String) {
        databaseClient.sql(
            """
            INSERT INTO workspace_file_blobs (
                workspace_id, file_hash, storage_key, detected_media_type, file_size_bytes, status
            ) VALUES (
                :workspaceId, :fileHash, 'storage/key.png', 'image/png', 1024, 'READY'
            )
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .bind("fileHash", fileHash)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private suspend fun insertRawAsset(
        assetId: String,
        workspaceId: String,
        sourceType: String,
        sourceProvider: String?,
        externalId: String?,
    ) {
        var spec = databaseClient.sql(
            """
            INSERT INTO media_assets (
                asset_id, workspace_id, source_type, file_hash, media_type, storage_key,
                file_size_bytes, status, created_at, source_provider, external_id
            ) VALUES (
                :assetId, :workspaceId, :sourceType, :fileHash, 'image/png', 'storage/key.png',
                1024, 'READY', CURRENT_TIMESTAMP, :sourceProvider, :externalId
            )
            """.trimIndent(),
        )
            .bind("assetId", assetId)
            .bind("workspaceId", workspaceId)
            .bind("sourceType", sourceType)
            .bind("fileHash", HASH)
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
        spec.fetch().rowsUpdated().awaitSingle()
    }

    private fun assertConstraintViolation(exception: Throwable?, constraint: String) {
        assertNotNull(exception)
        assertTrue(exception!!.stackTraceToString().contains(constraint), exception.stackTraceToString())
    }

    private fun externalAsset(
        assetId: String = "asset-external",
        workspaceId: String = "workspace-1",
        metadata: Map<String, Any>? = null,
    ) = MediaAsset(
        assetId = assetId,
        workspaceId = workspaceId,
        sourceType = MediaSourceType.EXTERNAL,
        fileHash = HASH,
        mediaType = "image/png",
        storageKey = "storage/key.png",
        fileSizeBytes = 1024,
        status = MediaAssetStatus.READY,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        sourceProvider = "unsplash",
        externalId = "photo-123",
        sourceUrl = "https://unsplash.com/photos/photo-123",
        authorName = "Jane Creator",
        authorUrl = "https://unsplash.com/@jane",
        metadata = metadata,
    )

    companion object {
        private const val HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

        @Container
        @JvmStatic
        val postgresContainer = PostgresTestContainerSupport.newContainer("media_asset_external_metadata")
    }
}
