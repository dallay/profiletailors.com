package com.profiletailors.smp.mediaprovider.unsplash

import com.profiletailors.smp.media.application.port.MediaProvider
import com.profiletailors.smp.media.application.port.ProviderExternalAsset
import com.profiletailors.smp.media.application.port.ProviderExternalId
import com.profiletailors.smp.media.application.port.ProviderPageMeta
import com.profiletailors.smp.media.application.port.ProviderSearchItem
import com.profiletailors.smp.media.application.port.ProviderSearchPage
import com.profiletailors.smp.media.domain.MediaAsset

class UnsplashAdapter(private val client: UnsplashClient, private val pageSize: Int) : MediaProvider {

    override val providerId: String = "unsplash"

    override suspend fun search(query: String, page: Int): ProviderSearchPage {
        val response = client.searchPhotos(query, page)
        return ProviderSearchPage(
            items = response.results.map { photo ->
                ProviderSearchItem(
                    externalId = ProviderExternalId("unsplash:${photo.id}"),
                    previewUrl = photo.urls.thumb,
                    fullUrl = photo.urls.full,
                    width = photo.width,
                    height = photo.height,
                    authorName = photo.user.name,
                    authorUrl = photo.user.links.html,
                    sourceUrl = photo.links.html,
                )
            },
            page = ProviderPageMeta(number = page, size = pageSize, total = response.total),
        )
    }

    override suspend fun import(workspaceId: String, externalId: ProviderExternalId): ProviderExternalAsset {
        require(externalId.value.startsWith("unsplash:")) { "externalId must start with unsplash:" }
        val photoId = externalId.value.removePrefix("unsplash:")
        val photo = client.getPhoto(photoId)
        val binary = client.downloadPhoto(photo)
        validateBinary(binary)

        return ProviderExternalAsset(
            externalId = externalId,
            mediaType = binary.mediaType,
            contentLength = binary.contentLength,
            bytes = binary.bytes,
            sourceProvider = "unsplash",
            sourceUrl = photo.links.html,
            authorName = photo.user.name,
            authorUrl = photo.user.links.html,
            metadata = buildMap {
                photo.color?.let { put("color", it) }
                photo.altDescription?.let { put("altDescription", it) }
                if (photo.tags.isNotEmpty()) put("tags", photo.tags.map { it.title })
                put("width", photo.width)
                put("height", photo.height)
                put("downloadLocation", photo.links.download)
            },
        )
    }

    private fun validateBinary(binary: UnsplashBinary) {
        if (binary.mediaType !in supportedImportTypes) {
            throw ProviderImportRejectedException("Unsupported media type: ${binary.mediaType}")
        }
        if (binary.contentLength > MediaAsset.MAX_FILE_SIZE_BYTES) {
            throw ProviderImportRejectedException("Provider asset exceeds maximum allowed size")
        }
    }

    companion object {
        private val supportedImportTypes = setOf("image/jpeg", "image/png", "image/gif", "image/webp")
    }
}
