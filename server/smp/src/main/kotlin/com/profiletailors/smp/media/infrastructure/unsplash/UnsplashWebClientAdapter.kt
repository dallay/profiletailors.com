package com.profiletailors.smp.media.infrastructure.unsplash

import com.fasterxml.jackson.annotation.JsonProperty
import com.profiletailors.smp.media.application.UnsplashPhoto
import com.profiletailors.smp.media.application.UnsplashPhotoNotFoundException
import com.profiletailors.smp.media.application.UnsplashPhotoProvider
import com.profiletailors.smp.media.application.UnsplashProviderException
import com.profiletailors.smp.media.application.UnsplashProviderNotConfiguredException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.core.ParameterizedTypeReference
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

/** Reactive HTTP adapter for the public Unsplash JSON API and image CDN. */
class UnsplashWebClientAdapter(private val webClient: WebClient, private val properties: UnsplashProperties) :
    UnsplashPhotoProvider {
    /**
     * Searches Unsplash for photos matching the optional query.
     *
     * @param query The search text, or `null` or blank to retrieve recent photos.
     * @return The matching photos.
     * @throws UnsplashProviderNotConfiguredException If Unsplash is not configured.
     * @throws UnsplashProviderException If Unsplash cannot fulfill the request.
     */
    override suspend fun search(query: String?): List<UnsplashPhoto> {
        requireConfigured()
        return try {
            if (query.isNullOrBlank()) {
                get<List<UnsplashPhotoResponse>>("/photos") {
                    queryParam("page", 1)
                    queryParam("per_page", properties.pageSize)
                }
            } else {
                get<UnsplashSearchResponse>("/search/photos") {
                    queryParam("query", query)
                    queryParam("page", 1)
                    queryParam("per_page", properties.pageSize)
                    queryParam("content_filter", "high")
                }.results
            }.map(UnsplashPhotoResponse::toPhoto)
        } catch (exception: WebClientResponseException) {
            throw mapProviderError(exception, null)
        } catch (@Suppress("TooGenericExceptionCaught") exception: Exception) {
            throw UnsplashProviderException("Unsplash search failed: ${exception.message}", exception)
        }
    }

    /**
     * Retrieves a photo by its external Unsplash identifier.
     *
     * @param externalId The photo identifier assigned by Unsplash.
     * @return The retrieved photo.
     * @throws UnsplashPhotoNotFoundException If no photo exists with the specified identifier.
     * @throws UnsplashProviderException If Unsplash cannot fulfill the request.
     */
    override suspend fun get(externalId: String): UnsplashPhoto {
        requireConfigured()
        return try {
            get<UnsplashPhotoResponse>("/photos/${encodePathSegment(externalId)}").toPhoto()
        } catch (exception: WebClientResponseException) {
            throw mapProviderError(exception, externalId)
        } catch (@Suppress("TooGenericExceptionCaught") exception: Exception) {
            throw UnsplashProviderException("Unsplash get photo failed: ${exception.message}", exception)
        }
    }

    /**
     * Downloads the photo's image content as a stream of byte arrays.
     *
     * @param photo The photo whose import URL identifies the image to download.
     * @return A flow containing chunks of the image content.
     * @throws UnsplashProviderNotConfiguredException If Unsplash integration is not configured.
     * @throws UnsplashProviderException If the import URL is invalid or does not use the approved image host.
     */
    override fun download(photo: UnsplashPhoto): Flow<ByteArray> {
        requireConfigured()
        validateProviderUri(photo.importUrl, ALLOWED_IMAGE_HOST)
        return webClient.get()
            .uri(photo.importUrl)
            .retrieve()
            .bodyToFlux(org.springframework.core.io.buffer.DataBuffer::class.java)
            .doOnDiscard(org.springframework.core.io.buffer.DataBuffer::class.java) { buffer ->
                DataBufferUtils.release(buffer)
            }
            .map { buffer ->
                val bytes = ByteArray(buffer.readableByteCount())
                buffer.read(bytes)
                DataBufferUtils.release(buffer)
                bytes
            }
            .onErrorMap(WebClientResponseException::class.java) { exception ->
                mapProviderError(exception, photo.externalId)
            }
            .onErrorMap({ exception -> exception !is UnsplashProviderException }) { exception ->
                UnsplashProviderException("Unsplash image download failed.", exception)
            }
            .asFlow()
    }

    /**
     * Records a download for the specified photo with the provider.
     *
     * @param photo The photo whose download should be tracked.
     * @throws UnsplashProviderNotConfiguredException if the provider is not configured.
     * @throws UnsplashProviderException if the download URL is invalid or the provider request fails.
     * @throws UnsplashPhotoNotFoundException if the provider cannot find the photo.
     */
    override suspend fun trackDownload(photo: UnsplashPhoto) {
        requireConfigured()
        validateApiUri(photo.downloadLocation)
        try {
            webClient.get()
                .uri(photo.downloadLocation)
                .headers(::addApiHeaders)
                .retrieve()
                .toBodilessEntity()
                .awaitSingle()
        } catch (exception: WebClientResponseException) {
            throw mapProviderError(exception, photo.externalId)
        } catch (@Suppress("TooGenericExceptionCaught") exception: Exception) {
            throw UnsplashProviderException("Unsplash download tracking failed.", exception)
        }
    }

    /**
     * Performs an authenticated GET request and deserializes the response body.
     *
     * @param path The request path.
     * @param configure Additional URI configuration.
     * @return The deserialized response body.
     */
    private suspend inline fun <reified T : Any> get(
        path: String,
        crossinline configure: org.springframework.web.util.UriBuilder.() -> Unit = {},
    ): T = webClient.get()
        .uri { builder ->
            builder.path(path).apply(configure).build()
        }
        .headers(::addApiHeaders)
        .retrieve()
        .bodyToMono(object : ParameterizedTypeReference<T>() {})
        .awaitSingle()

    /**
     * Adds the authentication and API version headers required by Unsplash requests.
     *
     * @param headers The HTTP headers to update.
     */
    private fun addApiHeaders(headers: HttpHeaders) {
        headers.set(HttpHeaders.AUTHORIZATION, "Client-ID ${properties.accessKey}")
        headers.set("Accept-Version", "v1")
    }

    /**
     * Ensures that Unsplash integration is configured.
     *
     * @throws UnsplashProviderNotConfiguredException if Unsplash is not configured.
     */
    private fun requireConfigured() {
        if (!properties.isConfigured) throw UnsplashProviderNotConfiguredException()
    }

    /**
     * Validates that a URL uses HTTP or HTTPS and matches the configured API host.
     *
     * @param value The API URL to validate.
     * @throws UnsplashProviderException If the URL uses an unsupported scheme or unexpected host.
     */
    private fun validateApiUri(value: String) {
        val uri = runCatching { URI.create(value) }
            .getOrElse { throw UnsplashProviderException("Unsplash returned a malformed API URL: $value.") }
        if (uri.scheme != "https") {
            throw UnsplashProviderException("Unsplash returned an insecure API URL (not HTTPS): $value.")
        }
        val baseUri = runCatching { URI.create(properties.baseUrl) }.getOrNull()
        if (baseUri != null && !uri.host.equals(baseUri.host, ignoreCase = true)) {
            throw UnsplashProviderException("Unsplash returned an unexpected API host: ${uri.host}.")
        }
    }

    /**
     * Validates that a provider URL uses HTTPS and the expected host.
     *
     * @param value The URL to validate.
     * @param expectedHost The permitted URL host.
     * @throws UnsplashProviderException If the URL does not use HTTPS or its host does not match the expected host.
     */
    private fun validateProviderUri(value: String, expectedHost: String) {
        val uri = runCatching { URI.create(value) }
            .getOrElse { throw UnsplashProviderException("Unsplash returned a malformed image URL: $value.") }
        if (uri.scheme != "https") {
            throw UnsplashProviderException("Unsplash returned an insecure image URL (not HTTPS): $value.")
        }
        if (!uri.host.equals(expectedHost, ignoreCase = true)) {
            throw UnsplashProviderException("Unsplash returned an unexpected image host: ${uri.host}.")
        }
    }

    /**
     * Maps an Unsplash HTTP error to a provider-specific exception.
     *
     * @param exception The HTTP error returned by Unsplash.
     * @param externalId The photo identifier associated with the request, if available.
     * @return A not-found exception for missing photos, a rate-limit exception for HTTP 429,
     *     or a general provider exception otherwise.
     */
    private fun mapProviderError(
        exception: WebClientResponseException,
        externalId: String?,
    ): UnsplashProviderException = when {
        exception.statusCode == HttpStatus.NOT_FOUND && externalId != null ->
            UnsplashPhotoNotFoundException(externalId)
        exception.statusCode == HttpStatus.TOO_MANY_REQUESTS ->
            UnsplashProviderException("Unsplash rate limit exceeded. Please try again later.", exception)
        else -> UnsplashProviderException("Unsplash is temporarily unavailable.", exception)
    }

    /**
     * Encodes a value for use as a URL path segment.
     *
     * @param value The path segment value to encode.
     * @return The UTF-8 encoded path segment.
     */
    private fun encodePathSegment(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

    private companion object {
        const val ALLOWED_IMAGE_HOST = "images.unsplash.com"
    }
}

private data class UnsplashSearchResponse(val results: List<UnsplashPhotoResponse> = emptyList())

private data class UnsplashPhotoResponse(
    val id: String,
    val description: String? = null,
    @JsonProperty("alt_description") val altDescription: String? = null,
    val urls: UnsplashUrlsResponse,
    val links: UnsplashLinksResponse,
    val user: UnsplashUserResponse,
) {
    /**
     * Converts the response data into an [UnsplashPhoto].
     *
     * @return The mapped photo, including attribution URLs and a fallback name when descriptions are unavailable.
     */
    fun toPhoto(): UnsplashPhoto = UnsplashPhoto(
        externalId = id,
        name = description?.takeIf(String::isNotBlank)
            ?: altDescription?.takeIf(String::isNotBlank)
            ?: "Photo by ${user.name}",
        previewUrl = urls.small,
        importUrl = urls.regular,
        sourceUrl = links.html.withAttributionParameters(),
        authorName = user.name,
        authorUrl = user.links.html.withAttributionParameters(),
        downloadLocation = links.downloadLocation,
    )
}

/**
 * Adds attribution query parameters to the URL.
 *
 * @return The URL with source and medium attribution parameters.
 */
private fun String.withAttributionParameters(): String = UriComponentsBuilder.fromUriString(this)
    .queryParam("utm_source", "profile_tailors")
    .queryParam("utm_medium", "referral")
    .build()
    .toUriString()

private data class UnsplashUrlsResponse(val small: String, val regular: String)

private data class UnsplashLinksResponse(
    val html: String,
    @JsonProperty("download_location") val downloadLocation: String,
)

private data class UnsplashUserResponse(val name: String, val links: UnsplashUserLinksResponse)

private data class UnsplashUserLinksResponse(val html: String)
