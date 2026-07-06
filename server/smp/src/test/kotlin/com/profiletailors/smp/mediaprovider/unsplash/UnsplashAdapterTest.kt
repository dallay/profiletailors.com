package com.profiletailors.smp.mediaprovider.unsplash

import com.profiletailors.smp.media.application.port.ProviderExternalId
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class UnsplashAdapterTest {

    @Test
    fun `search maps Unsplash response into provider-neutral items`() = runTest {
        val client = FakeUnsplashClient(
            searchPage = UnsplashSearchResponse(
                total = 32,
                totalPages = 2,
                results = listOf(
                    UnsplashPhoto(
                        id = "photo-123",
                        width = 1080,
                        height = 720,
                        color = "#112233",
                        altDescription = "Mountain lake",
                        urls = UnsplashPhotoUrls(
                            thumb = "https://images.unsplash.com/photo-123?w=200",
                            full = "https://images.unsplash.com/photo-123?w=2048",
                        ),
                        links = UnsplashPhotoLinks(
                            html = "https://unsplash.com/photos/photo-123",
                            download = "https://api.unsplash.com/photos/photo-123/download",
                        ),
                        user = UnsplashUser(
                            name = "Jane Creator",
                            links = UnsplashUserLinks(html = "https://unsplash.com/@jane"),
                        ),
                        tags = listOf(UnsplashTag("mountains"), UnsplashTag("lake")),
                    ),
                ),
            ),
        )
        val adapter = UnsplashAdapter(client, 20)

        val result = adapter.search(query = "mountains", page = 1)

        assertEquals(1, result.items.size)
        assertEquals(1, result.page.number)
        assertEquals(20, result.page.size)
        assertEquals(32, result.page.total)
        val item = result.items.single()
        assertEquals("unsplash:photo-123", item.externalId.value)
        assertEquals("https://images.unsplash.com/photo-123?w=200", item.previewUrl)
        assertEquals("https://images.unsplash.com/photo-123?w=2048", item.fullUrl)
        assertEquals("Jane Creator", item.authorName)
        assertEquals("https://unsplash.com/@jane", item.authorUrl)
        assertEquals("https://unsplash.com/photos/photo-123", item.sourceUrl)
    }

    @Test
    fun `import normalizes attribution and metadata`() = runTest {
        val bytes = byteArrayOf(0x01, 0x02, 0x03)
        val client = FakeUnsplashClient(
            photo = UnsplashPhoto(
                id = "photo-789",
                width = 1440,
                height = 960,
                color = "#abcdef",
                altDescription = "Soft studio portrait",
                urls = UnsplashPhotoUrls(
                    thumb = "https://images.unsplash.com/photo-789?w=200",
                    full = "https://images.unsplash.com/photo-789?w=2048",
                ),
                links = UnsplashPhotoLinks(
                    html = "https://unsplash.com/photos/photo-789",
                    download = "https://api.unsplash.com/photos/photo-789/download",
                ),
                user = UnsplashUser(
                    name = "John Doe",
                    links = UnsplashUserLinks(html = "https://unsplash.com/@john"),
                ),
                tags = listOf(UnsplashTag("portrait"), UnsplashTag("studio")),
            ),
            binary = UnsplashBinary(
                mediaType = "image/jpeg",
                contentLength = bytes.size.toLong(),
                bytes = flowOf(bytes),
            ),
        )
        val adapter = UnsplashAdapter(client, 20)

        val asset = adapter.import("workspace-1", ProviderExternalId("unsplash:photo-789"))

        assertEquals("unsplash", asset.sourceProvider)
        assertEquals("unsplash:photo-789", asset.externalId.value)
        assertEquals("https://unsplash.com/photos/photo-789", asset.sourceUrl)
        assertEquals("John Doe", asset.authorName)
        assertEquals("https://unsplash.com/@john", asset.authorUrl)
        assertEquals("image/jpeg", asset.mediaType)
        assertEquals(bytes.size.toLong(), asset.contentLength)
        assertEquals("#abcdef", asset.metadata["color"])
        assertEquals("Soft studio portrait", asset.metadata["altDescription"])
        assertEquals(listOf("portrait", "studio"), asset.metadata["tags"])
    }

    @Test
    fun `import rejects unsupported MIME`() = runTest {
        val client = FakeUnsplashClient(
            photo = minimalPhoto("photo-unsupported"),
            binary = UnsplashBinary(
                mediaType = "image/svg+xml",
                contentLength = 128,
                bytes = flowOf(byteArrayOf(0x01)),
            ),
        )
        val adapter = UnsplashAdapter(client, 20)

        val error = kotlin.runCatching {
            adapter.import("workspace-1", ProviderExternalId("unsplash:photo-unsupported"))
        }.exceptionOrNull()

        assertTrue(error is ProviderImportRejectedException)
        assertEquals("IMPORT_REJECTED", (error as ProviderImportRejectedException).errorCode)
    }

    @Test
    fun `import rejects payload larger than 500 MB`() = runTest {
        val client = FakeUnsplashClient(
            photo = minimalPhoto("photo-large"),
            binary = UnsplashBinary(
                mediaType = "image/png",
                contentLength = 500L * 1024 * 1024 + 1,
                bytes = flowOf(byteArrayOf(0x01)),
            ),
        )
        val adapter = UnsplashAdapter(client, 20)

        val error = kotlin.runCatching {
            adapter.import("workspace-1", ProviderExternalId("unsplash:photo-large"))
        }.exceptionOrNull()

        assertTrue(error is ProviderImportRejectedException)
        assertEquals("IMPORT_REJECTED", (error as ProviderImportRejectedException).errorCode)
    }

    @Test
    fun `properties require non-blank access key when enabled`() {
        val error = kotlin.runCatching {
            UnsplashProperties(
                enabled = true,
                accessKey = "",
                baseUrl = "https://api.unsplash.com",
                timeout = Duration.ofSeconds(5),
                pageSize = 20,
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `properties allow blank access key when disabled`() {
        val props =
            UnsplashProperties(
                enabled = false,
                accessKey = "",
                baseUrl = "https://api.unsplash.com",
                timeout = Duration.ofSeconds(5),
                pageSize = 20,
            )
        assertFalse(props.enabled)
        assertTrue(props.accessKey.isBlank())
    }

    private fun minimalPhoto(id: String) = UnsplashPhoto(
        id = id,
        width = 100,
        height = 100,
        color = null,
        altDescription = null,
        urls = UnsplashPhotoUrls(
            thumb = "https://images.unsplash.com/$id?w=200",
            full = "https://images.unsplash.com/$id?w=2048",
        ),
        links = UnsplashPhotoLinks(
            html = "https://unsplash.com/photos/$id",
            download = "https://api.unsplash.com/photos/$id/download",
        ),
        user = UnsplashUser(
            name = "Creator",
            links = UnsplashUserLinks(html = "https://unsplash.com/@creator"),
        ),
        tags = emptyList(),
    )

    private class FakeUnsplashClient(
        private val searchPage: UnsplashSearchResponse = UnsplashSearchResponse(0, 0, emptyList()),
        private val photo: UnsplashPhoto = minimalTestPhoto("photo-1"),
        private val binary: UnsplashBinary = UnsplashBinary("image/jpeg", 3, flowOf(byteArrayOf(0x01, 0x02, 0x03))),
    ) : UnsplashClient {
        override suspend fun searchPhotos(query: String, page: Int): UnsplashSearchResponse = searchPage
        override suspend fun getPhoto(photoId: String): UnsplashPhoto = photo
        override suspend fun downloadPhoto(photo: UnsplashPhoto): UnsplashBinary = binary
    }

    companion object {
        private fun minimalTestPhoto(id: String) = UnsplashPhoto(
            id = id,
            width = 100,
            height = 100,
            color = null,
            altDescription = null,
            urls = UnsplashPhotoUrls("https://images.unsplash.com/$id?w=200", "https://images.unsplash.com/$id?w=2048"),
            links = UnsplashPhotoLinks(
                "https://unsplash.com/photos/$id",
                "https://api.unsplash.com/photos/$id/download",
            ),
            user = UnsplashUser("Creator", UnsplashUserLinks("https://unsplash.com/@creator")),
            tags = emptyList(),
        )
    }
}
