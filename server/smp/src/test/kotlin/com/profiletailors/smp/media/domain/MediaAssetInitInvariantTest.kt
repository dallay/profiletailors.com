package com.profiletailors.smp.media.domain

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class MediaAssetInitInvariantTest {

    @Test
    fun `should reject external asset when source provider is null`() {
        assertThrows<IllegalArgumentException> {
            externalAsset(sourceProvider = null)
        }
    }

    @Test
    fun `should reject external asset when external id is null`() {
        assertThrows<IllegalArgumentException> {
            externalAsset(externalId = null)
        }
    }

    @Test
    fun `should reject external asset when source url is null`() {
        assertThrows<IllegalArgumentException> {
            externalAsset(sourceUrl = null)
        }
    }

    @Test
    fun `should reject external asset when source url is blank`() {
        assertThrows<IllegalArgumentException> {
            externalAsset(sourceUrl = " ")
        }
    }

    @Test
    fun `should accept external asset when provider external id and source url are present`() {
        assertDoesNotThrow {
            externalAsset(sourceProvider = "unsplash", externalId = "photo-123")
        }
    }

    @Test
    fun `should reject uploaded asset when source provider is present`() {
        assertThrows<IllegalArgumentException> {
            uploadedAsset(sourceProvider = "unsplash")
        }
    }

    @Test
    fun `should reject external asset when source provider is uppercase`() {
        assertThrows<IllegalArgumentException> {
            externalAsset(sourceProvider = "Unsplash")
        }
    }

    @Test
    fun `should reject external asset when source provider starts with digit`() {
        assertThrows<IllegalArgumentException> {
            externalAsset(sourceProvider = "1abc")
        }
    }

    @Test
    fun `should reject external asset when source provider is longer than 32 characters`() {
        assertThrows<IllegalArgumentException> {
            externalAsset(sourceProvider = "a".repeat(33))
        }
    }

    @Test
    fun `should accept external asset when source provider is valid snake case`() {
        assertDoesNotThrow {
            externalAsset(sourceProvider = "google_drive")
        }
    }

    private fun uploadedAsset(sourceProvider: String? = null) = MediaAsset(
        assetId = "asset-uploaded",
        workspaceId = "workspace-1",
        sourceType = MediaSourceType.UPLOADED,
        fileHash = HASH,
        mediaType = "image/png",
        storageKey = "storage/key.png",
        status = MediaAssetStatus.READY,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        sourceProvider = sourceProvider,
    )

    private fun externalAsset(
        sourceProvider: String? = "unsplash",
        externalId: String? = "photo-123",
        sourceUrl: String? = "https://unsplash.com/photos/photo-123",
    ) = MediaAsset(
        assetId = "asset-external",
        workspaceId = "workspace-1",
        sourceType = MediaSourceType.EXTERNAL,
        fileHash = HASH,
        mediaType = "image/png",
        storageKey = "storage/key.png",
        status = MediaAssetStatus.READY,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        sourceProvider = sourceProvider,
        externalId = externalId,
        sourceUrl = sourceUrl,
    )

    companion object {
        private const val HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
