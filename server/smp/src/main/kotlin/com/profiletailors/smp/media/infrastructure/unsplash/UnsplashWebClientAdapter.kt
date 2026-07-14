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
        }
    }

    override suspend fun get(externalId: String): UnsplashPhoto {
        requireConfigured()
        return try {
            get<UnsplashPhotoResponse>("/photos/${encodePathSegment(externalId)}").toPhoto()
        } catch (exception: WebClientResponseException) {
            throw mapProviderError(exception, externalId)
        }
    }

    override fun download(photo: UnsplashPhoto): Flow<ByteArray> {
        requireConfigured()
        validateProviderUri(photo.importUrl, ALLOWED_IMAGE_HOST)
        return webClient.get()
            .uri(photo.importUrl)
            .retrieve()
            .bodyToFlux(org.springframework.core.io.buffer.DataBuffer::class.java)
            .map { buffer ->
                val bytes = ByteArray(buffer.readableByteCount())
                buffer.read(bytes)
                DataBufferUtils.release(buffer)
                bytes
            }
            .asFlow()
    }

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
        }
    }

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

    private fun addApiHeaders(headers: HttpHeaders) {
        headers.set(HttpHeaders.AUTHORIZATION, "Client-ID ${properties.accessKey}")
        headers.set("Accept-Version", "v1")
    }

    private fun requireConfigured() {
        if (!properties.isConfigured) throw UnsplashProviderNotConfiguredException()
    }

    private fun validateApiUri(value: String) {
        val uri = URI.create(value)
        val baseUri = URI.create(properties.baseUrl)
        if (uri.scheme != "https" && uri.scheme != "http") {
            throw UnsplashProviderException("Unsplash returned an invalid API URL.")
        }
        if (!uri.host.equals(baseUri.host, ignoreCase = true)) {
            throw UnsplashProviderException("Unsplash returned an unexpected API host.")
        }
    }

    private fun validateProviderUri(value: String, expectedHost: String) {
        val uri = URI.create(value)
        if (uri.scheme != "https" || !uri.host.equals(expectedHost, ignoreCase = true)) {
            throw UnsplashProviderException("Unsplash returned an unexpected image host.")
        }
    }

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
