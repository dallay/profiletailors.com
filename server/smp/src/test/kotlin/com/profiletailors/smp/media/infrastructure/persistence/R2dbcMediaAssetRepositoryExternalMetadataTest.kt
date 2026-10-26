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
import java.util.UUID

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
        seedWorkspace(WORKSPACE_1)
        insertWorkspaceBlob(WORKSPACE_1, HASH)
        val asset = externalAsset(
            metadata = mapOf(
                "provider" to mapOf("downloadLocation" to "https://api.unsplash.com/photos/photo-123/download"),
                "palette" to listOf("#000000", "#ffffff"),
            ),
        )

        repository.create(asset)

        val found = requireNotNull(repository.findByWorkspaceAndId(WORKSPACE_1, ASSET_EXTERNAL))
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
        seedWorkspace(WORKSPACE_2)
        insertWorkspaceBlob(WORKSPACE_2, HASH)

        val exception = kotlin.runCatching {
            insertRawAsset(
                assetId = ASSET_UPLOADED_PROVIDER,
                workspaceId = WORKSPACE_2,
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
        seedWorkspace(WORKSPACE_3)
        insertWorkspaceBlob(WORKSPACE_3, HASH)

        val exception = kotlin.runCatching {
            insertRawAsset(
                assetId = ASSET_MISSING_PROVIDER,
                workspaceId = WORKSPACE_3,
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
        seedWorkspace(WORKSPACE_4)
        insertWorkspaceBlob(WORKSPACE_4, HASH)

        val exception = kotlin.runCatching {
            insertRawAsset(
                assetId = ASSET_MISSING_ID,
                workspaceId = WORKSPACE_4,
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
        seedWorkspace(WORKSPACE_5)
        insertWorkspaceBlob(WORKSPACE_5, HASH)
        repository.create(
            externalAsset(
                assetId = ASSET_JSONB,
                workspaceId = WORKSPACE_5,
                metadata = mapOf("nested" to mapOf("width" to 1080, "tags" to listOf("social", "hero"))),
            ),
        )

        val value = databaseClient.sql(
            """
            SELECT metadata #>> '{nested,tags,1}' AS tag
            FROM media_assets
            WHERE asset_id = '$ASSET_JSONB'
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
            .bind("workspaceId", UUID.fromString(workspaceId))
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
            .bind("workspaceId", UUID.fromString(workspaceId))
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
            .bind("assetId", UUID.fromString(assetId))
            .bind("workspaceId", UUID.fromString(workspaceId))
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
        assetId: String = ASSET_EXTERNAL,
        workspaceId: String = WORKSPACE_1,
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
        private const val WORKSPACE_1 = "d3d9800f-120a-501e-ad01-401e54e8f766"
        private const val ASSET_EXTERNAL = "9d119052-5d20-5ac7-baf5-0276e5c0cc93"
        private const val WORKSPACE_2 = "57af0326-afba-57ec-83e4-15ec87ecc8bc"
        private const val ASSET_UPLOADED_PROVIDER = "b3c5e1bb-b4cf-5ec7-9314-bfff93286719"
        private const val WORKSPACE_3 = "5bcd87e0-6e75-5367-b969-99c74c860cdc"
        private const val ASSET_MISSING_PROVIDER = "30019f31-931a-57c5-b35e-f22ce88cf23f"
        private const val WORKSPACE_4 = "591c3e24-e1fc-55ea-8be4-5dc9fafb4a6a"
        private const val ASSET_MISSING_ID = "bcf2237d-2d9e-5d0d-8797-9160c6b33204"
        private const val WORKSPACE_5 = "aed2d3a0-8415-57ed-b2b8-d5dde80ca7c7"
        private const val ASSET_JSONB = "e18c2167-dd44-5b64-a82c-6bc3c98d4f51"

        private const val HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

        @Container
        @JvmStatic
        val postgresContainer = PostgresTestContainerSupport.newContainer("media_asset_external_metadata")
    }
}
