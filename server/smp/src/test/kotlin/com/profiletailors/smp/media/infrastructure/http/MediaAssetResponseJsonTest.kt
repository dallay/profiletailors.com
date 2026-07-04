package com.profiletailors.smp.media.infrastructure.http

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MediaAssetResponseJsonTest {

    private val objectMapper = ObjectMapper().findAndRegisterModules()

    @Test
    fun `legacy response serializes external metadata fields as null`() {
        val response = MediaAssetResponse(
            assetId = "asset-legacy",
            workspaceId = "workspace-1",
            sourceType = "UPLOADED",
            mediaType = "image/png",
            status = "READY",
            originalFilename = "asset.png",
            fileSizeBytes = 1024,
            createdAt = "2026-01-01T00:00:00Z",
        )

        val json = objectMapper.writeValueAsString(response)
        val tree = objectMapper.readTree(json)

        assertMissingOrJsonNull(tree, "sourceProvider")
        assertMissingOrJsonNull(tree, "externalId")
        assertMissingOrJsonNull(tree, "sourceUrl")
        assertMissingOrJsonNull(tree, "authorName")
        assertMissingOrJsonNull(tree, "authorUrl")
        assertMissingOrJsonNull(tree, "metadata")
    }

    @Test
    fun `external response serializes every external metadata field`() {
        val response = MediaAssetResponse(
            assetId = "asset-external",
            workspaceId = "workspace-1",
            sourceType = "EXTERNAL",
            mediaType = "image/png",
            status = "READY",
            originalFilename = null,
            fileSizeBytes = 2048,
            createdAt = "2026-01-01T00:00:00Z",
            sourceProvider = "unsplash",
            externalId = "photo-123",
            sourceUrl = "https://unsplash.com/photos/photo-123",
            authorName = "Jane Creator",
            authorUrl = "https://unsplash.com/@jane",
            metadata = mapOf("palette" to listOf("#000000", "#ffffff")),
        )

        val tree = objectMapper.readTree(objectMapper.writeValueAsString(response))

        assertEquals("unsplash", tree.get("sourceProvider").asText())
        assertEquals("photo-123", tree.get("externalId").asText())
        assertEquals("https://unsplash.com/photos/photo-123", tree.get("sourceUrl").asText())
        assertEquals("Jane Creator", tree.get("authorName").asText())
        assertEquals("https://unsplash.com/@jane", tree.get("authorUrl").asText())
        assertEquals("#000000", tree.get("metadata").get("palette").first().asText())
    }

    private fun assertMissingOrJsonNull(tree: JsonNode, fieldName: String) {
        val node = tree.get(fieldName)
        assertTrue(node == null || node.isNull, "$fieldName should be missing or JSON null")
    }
}
