package com.profiletailors.smp.media.application

import com.profiletailors.smp.media.application.port.ImportProviderAssetQuery
import com.profiletailors.smp.media.application.port.MediaProvider
import com.profiletailors.smp.media.application.port.ProviderExternalAsset
import com.profiletailors.smp.media.application.port.ProviderExternalId
import com.profiletailors.smp.media.application.port.ProviderPageMeta
import com.profiletailors.smp.media.application.port.ProviderSearchItem
import com.profiletailors.smp.media.application.port.ProviderSearchPage
import com.profiletailors.smp.media.application.port.SearchProviderPhotosQuery
import com.profiletailors.smp.media.application.port.UnsupportedProviderException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class MediaProviderHandlersTest {

    @Test
    fun `SearchProviderPhotosHandler selects provider by providerId from multiple adapters`() = runTest {
        val unsplashProvider = FakeMediaProvider(
            providerId = "unsplash",
            searchPage = providerSearchPage("unsplash:photo-1"),
            importedAsset = providerExternalAsset("unsplash:photo-1"),
        )
        val secondProvider = FakeMediaProvider(
            providerId = "pexels",
            searchPage = providerSearchPage("pexels:photo-2"),
            importedAsset = providerExternalAsset("pexels:photo-2"),
        )
        val handler = SearchProviderPhotosHandler(listOf(unsplashProvider, secondProvider))

        val result = handler.handle(
            SearchProviderPhotosQuery(
                providerId = "pexels",
                workspaceId = "workspace-1",
                query = "mountains",
                page = 2,
            ),
        )

        assertEquals("pexels:photo-2", result.items.single().externalId.value)
        assertEquals(0, unsplashProvider.searchCalls)
        assertEquals(1, secondProvider.searchCalls)
        assertEquals("mountains", secondProvider.lastSearchQuery)
        assertEquals(2, secondProvider.lastSearchPage)
    }

    @Test
    fun `ImportProviderAssetHandler selects provider by providerId from multiple adapters`() = runTest {
        val unsplashAsset = providerExternalAsset("unsplash:photo-1")
        val pexelsAsset = providerExternalAsset("pexels:photo-2")
        val unsplashProvider = FakeMediaProvider(
            providerId = "unsplash",
            searchPage = providerSearchPage("unsplash:photo-1"),
            importedAsset = unsplashAsset,
        )
        val secondProvider = FakeMediaProvider(
            providerId = "pexels",
            searchPage = providerSearchPage("pexels:photo-2"),
            importedAsset = pexelsAsset,
        )
        val handler = ImportProviderAssetHandler(listOf(unsplashProvider, secondProvider))

        val result = handler.handle(
            ImportProviderAssetQuery(
                providerId = "pexels",
                workspaceId = "workspace-1",
                externalId = "pexels:photo-2",
            ),
        )

        assertSame(pexelsAsset, result)
        assertEquals(0, unsplashProvider.importCalls)
        assertEquals(1, secondProvider.importCalls)
        assertEquals("workspace-1", secondProvider.lastImportWorkspaceId)
        assertEquals("pexels:photo-2", secondProvider.lastImportExternalId?.value)
    }

    @Test
    fun `SearchProviderPhotosHandler rejects unsupported provider ids without depending on concrete adapters`() =
        runTest {
            val handler = SearchProviderPhotosHandler(
                listOf(
                    FakeMediaProvider(
                        providerId = "unsplash",
                        searchPage = providerSearchPage("unsplash:photo-1"),
                        importedAsset = providerExternalAsset("unsplash:photo-1"),
                    ),
                ),
            )

            val exception = assertFailsWith<UnsupportedProviderException> {
                handler.handle(
                    SearchProviderPhotosQuery(
                        providerId = "pexels",
                        workspaceId = "workspace-1",
                        query = "mountains",
                        page = 1,
                    ),
                )
            }

            assertEquals("No media provider registered for 'pexels'", exception.message)
        }

    @Test
    fun `ImportProviderAssetHandler rejects unsupported provider ids without depending on concrete adapters`() =
        runTest {
            val handler = ImportProviderAssetHandler(
                listOf(
                    FakeMediaProvider(
                        providerId = "unsplash",
                        searchPage = providerSearchPage("unsplash:photo-1"),
                        importedAsset = providerExternalAsset("unsplash:photo-1"),
                    ),
                ),
            )

            val exception = assertFailsWith<UnsupportedProviderException> {
                handler.handle(
                    ImportProviderAssetQuery(
                        providerId = "pexels",
                        workspaceId = "workspace-1",
                        externalId = "pexels:photo-1",
                    ),
                )
            }

            assertEquals("No media provider registered for 'pexels'", exception.message)
        }

    private class FakeMediaProvider(
        override val providerId: String,
        private val searchPage: ProviderSearchPage,
        private val importedAsset: ProviderExternalAsset,
    ) : MediaProvider {
        var searchCalls: Int = 0
        var importCalls: Int = 0
        var lastSearchQuery: String? = null
        var lastSearchPage: Int? = null
        var lastImportWorkspaceId: String? = null
        var lastImportExternalId: ProviderExternalId? = null

        override suspend fun search(query: String, page: Int): ProviderSearchPage {
            searchCalls += 1
            lastSearchQuery = query
            lastSearchPage = page
            return searchPage
        }

        override suspend fun import(workspaceId: String, externalId: ProviderExternalId): ProviderExternalAsset {
            importCalls += 1
            lastImportWorkspaceId = workspaceId
            lastImportExternalId = externalId
            return importedAsset
        }
    }

    private fun providerSearchPage(externalId: String): ProviderSearchPage = ProviderSearchPage(
        items = listOf(
            ProviderSearchItem(
                externalId = ProviderExternalId(externalId),
                previewUrl = "https://example.test/preview",
                fullUrl = "https://example.test/full",
                width = 1200,
                height = 800,
                authorName = "Author",
                authorUrl = "https://example.test/author",
                sourceUrl = "https://example.test/source",
            ),
        ),
        page = ProviderPageMeta(number = 1, size = 20, total = 1),
    )

    private fun providerExternalAsset(externalId: String): ProviderExternalAsset = ProviderExternalAsset(
        externalId = ProviderExternalId(externalId),
        mediaType = "image/jpeg",
        contentLength = 3,
        bytes = flowOf(byteArrayOf(1, 2, 3)),
        sourceProvider = externalId.substringBefore(':'),
        sourceUrl = "https://example.test/source",
        authorName = "Author",
        authorUrl = "https://example.test/author",
    )
}
