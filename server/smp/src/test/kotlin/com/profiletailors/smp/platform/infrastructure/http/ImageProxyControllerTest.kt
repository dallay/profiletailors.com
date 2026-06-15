package com.profiletailors.smp.platform.infrastructure.http

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.net.URI
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking

class ImageProxyControllerTest {

    private val webClient = Mockito.mock(WebClient::class.java)
    private val controller = ImageProxyController(webClient)

    // -----------------------------------------------------------------------
    // allowedHosts
    // -----------------------------------------------------------------------

    @Test
    fun `allows licdn com host`() {
        assertThat(controller.allowedHosts).contains("media.licdn.com")
    }

    @Test
    fun `allows twimg com host`() {
        assertThat(controller.allowedHosts).contains("pbs.twimg.com")
    }

    @Test
    fun `allows facebook cdn hosts`() {
        assertThat(controller.allowedHosts)
            .contains("platform-lookaside.fbsbx.com", "scontent.xx.fbcdn.net")
    }

    @Test
    fun `allows instagram cdn host`() {
        assertThat(controller.allowedHosts)
            .contains("instagram.fbog1-1.fna.fbcdn.net")
    }

    @Test
    fun `does not allow arbitrary hosts`() {
        assertThat(controller.allowedHosts)
            .doesNotContain("evil.com", "media.evil.com", "licdn.com", "example.com")
    }

    // -----------------------------------------------------------------------
    // proxyImage — validation (no HTTP call made)
    // -----------------------------------------------------------------------

    @Test
    fun `returns 400 when host is not in allowed list`() = runBlocking<Unit> {
        val response = controller.proxyImage("https://evil.com/image.jpg")
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `returns 400 when URL has no host`() = runBlocking<Unit> {
        val response = controller.proxyImage("not-a-valid-url")
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `returns 400 when host is null`() = runBlocking<Unit> {
        val response = controller.proxyImage("https:///path-only")
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `rejects malformed URLs with 400`() = runBlocking<Unit> {
        val response = controller.proxyImage("not a valid url %%%")
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `rejects http scheme`() = runBlocking<Unit> {
        val response = controller.proxyImage("http://media.licdn.com/media/test.jpg")
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `matches host case-insensitively`() = runBlocking<Unit> {
        mockUpstreamResponse(MediaType.IMAGE_JPEG, byteArrayOf(1, 2, 3))

        val response = controller.proxyImage("https://MEDIA.LINKDN.COM/media/test.jpg")

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    }

    // -----------------------------------------------------------------------
    // proxyImage — success
    // -----------------------------------------------------------------------

    @Test
    fun `proxies image and returns upstream content type`() = runBlocking<Unit> {
        val imageBytes = byteArrayOf(1, 2, 3)
        mockUpstreamResponse(MediaType.IMAGE_GIF, imageBytes)

        val response = controller.proxyImage("https://media.licdn.com/media/test.jpg")

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.headers.contentType).isEqualTo(MediaType.IMAGE_GIF)
        assertThat(response.body).isEqualTo(imageBytes)
    }

    @Test
    fun `proxies image from twimg with upstream content type`() = runBlocking<Unit> {
        val imageBytes = byteArrayOf(4, 5, 6)
        mockUpstreamResponse(MediaType.IMAGE_PNG, imageBytes)

        val response = controller.proxyImage("https://pbs.twimg.com/media/test.png")

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.headers.contentType).isEqualTo(MediaType.IMAGE_PNG)
        assertThat(response.body).isEqualTo(imageBytes)
    }

    @Test
    fun `proxies image from fbsbx with upstream content type`() = runBlocking<Unit> {
        val imageBytes = byteArrayOf(7, 8, 9)
        mockUpstreamResponse(MediaType.IMAGE_JPEG, imageBytes)

        val response = controller.proxyImage("https://platform-lookaside.fbsbx.com/media/img.jpg")

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.headers.contentType).isEqualTo(MediaType.IMAGE_JPEG)
        assertThat(response.body).isEqualTo(imageBytes)
    }

    // -----------------------------------------------------------------------
    // proxyImage — upstream error propagation
    // -----------------------------------------------------------------------

    @Test
    fun `forwards upstream 404 status`() = runBlocking<Unit> {
        mockUpstreamError(404)

        val response = controller.proxyImage("https://media.licdn.com/media/missing.jpg")

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `forwards upstream 502 status`() = runBlocking<Unit> {
        mockUpstreamError(502)

        val response = controller.proxyImage("https://media.licdn.com/media/error.jpg")

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_GATEWAY)
    }

    // -----------------------------------------------------------------------
    // Helpers — mock the WebClient fluent chain
    // -----------------------------------------------------------------------

    /**
     * Mock the WebClient chain so [awaitEntity] returns a [ResponseEntity]
     * with the given content type and body.
     *
     * [awaitEntity] is an inline Kotlin extension that eventually calls
     * [WebClient.ResponseSpec.toEntity] with a [ParameterizedTypeReference],
     * so we mock that bridge method.
     */
    @Suppress("UNCHECKED_CAST")
    private fun mockUpstreamResponse(contentType: MediaType, body: ByteArray) {
        val spec = Mockito.mock(WebClient.RequestHeadersUriSpec::class.java)
        val headersSpec = Mockito.mock(WebClient.RequestHeadersSpec::class.java)
        val responseSpec = Mockito.mock(WebClient.ResponseSpec::class.java)

        `when`(webClient.get()).thenReturn(spec)
        `when`(spec.uri(any(URI::class.java))).thenReturn(spec)
        `when`(spec.accept(any(MediaType::class.java), any(MediaType::class.java), any(MediaType::class.java)))
            .thenReturn(headersSpec)
        `when`(headersSpec.retrieve()).thenReturn(responseSpec)

        val entity = ResponseEntity.ok()
            .contentType(contentType)
            .body(body)

        `when`(
            responseSpec.toEntity(any(ParameterizedTypeReference::class.java) as ParameterizedTypeReference<ByteArray>),
        ).thenReturn(Mono.just(entity))
    }

    @Suppress("UNCHECKED_CAST")
    private fun mockUpstreamError(statusCode: Int) {
        val spec = Mockito.mock(WebClient.RequestHeadersUriSpec::class.java)
        val headersSpec = Mockito.mock(WebClient.RequestHeadersSpec::class.java)
        val responseSpec = Mockito.mock(WebClient.ResponseSpec::class.java)

        `when`(webClient.get()).thenReturn(spec)
        `when`(spec.uri(any(URI::class.java))).thenReturn(spec)
        `when`(spec.accept(any(MediaType::class.java), any(MediaType::class.java), any(MediaType::class.java)))
            .thenReturn(headersSpec)
        `when`(headersSpec.retrieve()).thenReturn(responseSpec)
        `when`(
            responseSpec.toEntity(any(ParameterizedTypeReference::class.java) as ParameterizedTypeReference<ByteArray>),
        ).thenThrow(
            WebClientResponseException.create(
                statusCode,
                "Upstream error",
                HttpHeaders.EMPTY,
                ByteArray(0),
                StandardCharsets.UTF_8,
                null,
            ),
        )
    }
}
