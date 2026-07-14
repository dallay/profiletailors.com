package com.profiletailors.smp.media.application

import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.query.Query
import kotlinx.coroutines.flow.Flow

/** A photo returned by the configured Unsplash provider. */
data class UnsplashPhoto(
    val externalId: String,
    val name: String,
    val previewUrl: String,
    val importUrl: String,
    val sourceUrl: String,
    val authorName: String,
    val authorUrl: String,
    val downloadLocation: String,
)

/** Port implemented by the Unsplash HTTP adapter. */
interface UnsplashPhotoProvider {
    /**
 * Lists editorial photos when [query] is blank and searches for matching photos otherwise.
 *
 * @param query The optional search query.
 * @return The matching or editorial photos.
 */
    suspend fun search(query: String?): List<UnsplashPhoto>

    /**
 * Resolves the current canonical provider data for a photo.
 *
 * @param externalId The provider identifier of the photo.
 * @return The canonical photo data.
 */
    suspend fun get(externalId: String): UnsplashPhoto

    /**
 * Streams the selected photo's bytes from the provider CDN.
 *
 * @param photo The photo to download.
 * @return A stream of the photo's byte data.
 */
    fun download(photo: UnsplashPhoto): Flow<ByteArray>

    /**
 * Records the provider-required download event for a selected photo.
 *
 * @param photo The photo whose download event should be recorded.
 */
    suspend fun trackDownload(photo: UnsplashPhoto)
}

/** Query used by the HTTP adapter to browse or search Unsplash. */
data class SearchUnsplashPhotosQuery(val query: String?) : Query<List<UnsplashPhoto>>

/** Command that imports one Unsplash photo into a workspace media library. */
data class ImportUnsplashPhotoCommand(val workspaceId: String, val externalId: String) :
    CommandWithResult<MediaAssetSummary>

/** Settings applied while persisting externally sourced media. */
data class UnsplashImportSettings(
    val storageBucket: String,
    val maxFileSizeBytes: Long,
    val maxCreationsPerHour: Int,
) {
    init {
        require(storageBucket.isNotBlank()) { "Unsplash storage bucket must not be blank" }
        require(maxFileSizeBytes > 0) { "Unsplash max file size must be greater than zero" }
        require(maxCreationsPerHour > 0) { "Unsplash hourly creation limit must be greater than zero" }
    }
}

/** Base error raised when the configured Unsplash provider cannot complete an operation. */
open class UnsplashProviderException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Raised when the Unsplash adapter is disabled or has no access key. */
class UnsplashProviderNotConfiguredException :
    UnsplashProviderException("Unsplash is not configured for this environment.")

/** Raised when a requested Unsplash photo no longer exists. */
class UnsplashPhotoNotFoundException(val externalId: String) :
    UnsplashProviderException("Unsplash photo $externalId was not found.")

/** Raised when an imported provider image exceeds the configured safety limit. */
class UnsplashPhotoTooLargeException(val actualSize: Long, val maxAllowed: Long) :
    UnsplashProviderException(
        "The selected Unsplash photo ($actualSize bytes) exceeds the import size limit ($maxAllowed bytes).",
    )
