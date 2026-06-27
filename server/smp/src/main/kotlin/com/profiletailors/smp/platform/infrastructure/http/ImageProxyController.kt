package com.profiletailors.smp.platform.infrastructure.http

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitEntity
import java.net.URI
import java.time.Duration

@RestController
@RequestMapping(value = ["/api/media"])
@Tag(name = "Media Proxy", description = "Proxy external media to avoid browser blocking")
class ImageProxyController(private val webClient: WebClient) {
    companion object {
        private val CACHE_DURATION = Duration.ofMinutes(30)
    }

    private fun validateUrl(url: String): URI? {
        val uri = try {
            URI.create(url)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val host = uri.host?.lowercase()
        return if (uri.scheme == "https" && host != null && host in allowedHosts) uri else null
    }

    internal val allowedHosts = setOf(
        "media.licdn.com",
        "pbs.twimg.com",
        "platform-lookaside.fbsbx.com",
        "scontent.xx.fbcdn.net",
        "instagram.fbog1-1.fna.fbcdn.net",
    )

    @Operation(summary = "Proxy external image to bypass browser ad-blocker restrictions")
    @GetMapping("/proxy")
    suspend fun proxyImage(@RequestParam url: String): ResponseEntity<ByteArray> {
        val uri = validateUrl(url) ?: return ResponseEntity.badRequest().build()

        return try {
            val upstream = webClient.get()
                .uri(uri)
                .accept(MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, MediaType.IMAGE_GIF)
                .retrieve()
                .awaitEntity<ByteArray>()

            val upstreamHeaders = upstream.headers
            val upstreamContentType = upstreamHeaders.contentType
                ?: MediaType.APPLICATION_OCTET_STREAM

            ResponseEntity.ok()
                .contentType(upstreamContentType)
                .cacheControl(CacheControl.maxAge(CACHE_DURATION))
                .body(upstream.body)
        } catch (e: WebClientResponseException) {
            ResponseEntity.status(e.statusCode).build()
        }
    }
}
