package com.profiletailors.smp.mediaprovider.unsplash

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.util.concurrent.TimeoutException

/**
 * Reactive HTTP client for the Unsplash REST API.
 *
 * The client is intentionally thin: it owns the `Authorization: Client-ID <key>`
 * header and the explicit connect/read timeout (mirroring [UnsplashProperties.timeout]).
 * It throws typed [UnsplashProviderException] subclasses so the adapter sees
 * provider-neutral errors.
 *
 * Search / getPhoto may be retried by the caller; `downloadPhoto` must NOT
 * retry because each call performs a side-effect (Unsplash rate-limits
 * `downloadLocation` calls).
 */
class UnsplashWebClient(private val webClient: WebClient, private val properties: UnsplashProperties) :
    UnsplashClient {

    override suspend fun searchPhotos(query: String, page: Int): UnsplashSearchResponse {
        val request = webClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/search/photos")
                    .queryParam("query", query)
                    .queryParam("page", page)
                    .queryParam("per_page", properties.pageSize)
                    .build()
            }
            .accept(MediaType.APPLICATION_JSON)
            .headers { headers -> headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader()) }
            .retrieve()
            .bodyToMono(UnsplashSearchResponse::class.java)

        return try {
            request.awaitSingle()
        } catch (e: WebClientResponseException) {
            throw translateHttpError(e)
        } catch (e: TimeoutException) {
            throw ProviderUnavailableException("Unsplash request timed out", e)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            throw ProviderUnavailableException("Unsplash request failed", e)
        }
    }

    override suspend fun getPhoto(photoId: String): UnsplashPhoto {
        require(photoId.isNotBlank()) { "photoId must not be blank" }
        val request = webClient.get()
            .uri { uriBuilder -> uriBuilder.path("/photos/{id}").build(photoId) }
            .accept(MediaType.APPLICATION_JSON)
            .headers { headers -> headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader()) }
            .retrieve()
            .bodyToMono(UnsplashPhoto::class.java)

        return try {
            request.awaitSingle()
        } catch (e: WebClientResponseException) {
            throw translateHttpError(e)
        } catch (e: TimeoutException) {
            throw ProviderUnavailableException("Unsplash request timed out", e)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            throw ProviderUnavailableException("Unsplash request failed", e)
        }
    }

    @Suppress("ThrowsCount") // Distinct provider, timeout, and transport failures map to different API semantics.
    override suspend fun downloadPhoto(photo: UnsplashPhoto): UnsplashBinary {
        // Use the download endpoint when provided (it counts toward Unsplash's
        // download quota), otherwise fall back to the regular `urls.full`.
        val url = photo.links.download.takeIf { it.isNotBlank() } ?: photo.urls.full

        val responseMono = webClient.get()
            .uri(url)
            .accept(MediaType.APPLICATION_OCTET_STREAM, MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, MediaType.ALL)
            .headers { headers -> headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader()) }
            .exchangeToMono { response ->
                val status = response.statusCode()
                if (status.is4xxClientError) {
                    Mono.error(
                        WebClientResponseException.create(
                            status.value(),
                            "Client error",
                            response.headers().asHttpHeaders(),
                            ByteArray(0),
                            Charsets.UTF_8,
                        ),
                    )
                } else if (status.is5xxServerError) {
                    Mono.error(
                        ProviderUnavailableException("Unsplash server error ${status.value()}"),
                    )
                } else {
                    val ct = response.headers().contentType().orElse(MediaType.APPLICATION_OCTET_STREAM)
                    val mediaType = ct.toString()
                    val contentLength: Long = response.headers().contentLength().orElse(0L)
                    response.bodyToFlux(DataBuffer::class.java)
                        .map { buffer ->
                            val bytes = ByteArray(buffer.readableByteCount())
                            buffer.read(bytes)
                            bytes
                        }
                        .collectList()
                        .map { chunks -> DownloadEnvelope(mediaType, contentLength, chunks) }
                }
            }

        val envelope = try {
            responseMono.awaitSingle()
        } catch (e: WebClientResponseException) {
            throw translateHttpError(e)
        } catch (e: TimeoutException) {
            throw ProviderUnavailableException("Unsplash download timed out", e)
        } catch (e: ProviderUnavailableException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            throw ProviderUnavailableException("Unsplash download failed", e)
        }

        return UnsplashBinary(
            mediaType = envelope.mediaType,
            contentLength = envelope.contentLength,
            bytes = envelope.chunks.asFlow(),
        )
    }

    private fun authorizationHeader(): String = "Client-ID ${properties.accessKey}"

    private fun translateHttpError(e: WebClientResponseException): UnsplashProviderException {
        val statusCode = HttpStatus.valueOf(e.statusCode.value())
        return when {
            statusCode == HttpStatus.TOO_MANY_REQUESTS ->
                UnsplashRateLimitedException(extractRetryAfter(e))
            statusCode.is4xxClientError -> ProviderErrorException("Unsplash returned ${statusCode.value()}")
            statusCode.is5xxServerError -> ProviderUnavailableException(
                "Unsplash server error ${statusCode.value()}",
                e,
            )
            else -> ProviderErrorException("Unsplash returned ${statusCode.value()}")
        }
    }

    private fun extractRetryAfter(e: WebClientResponseException): Int =
        e.headers["Retry-After"]?.firstOrNull()?.toIntOrNull() ?: DEFAULT_RETRY_AFTER

    private data class DownloadEnvelope(val mediaType: String, val contentLength: Long, val chunks: List<ByteArray>)

    companion object {
        private const val DEFAULT_RETRY_AFTER = 5
    }
}
