package com.profiletailors.smp.platform.infrastructure.http

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
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
    // proxyImage — validation
    // -----------------------------------------------------------------------

    @Test
    fun `returns 400 when host is not in allowed list`() = runBlocking {
        val response = controller.proxyImage("https://evil.com/image.jpg")
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `returns 400 when URL has no host`() = runBlocking {
        val response = controller.proxyImage("not-a-valid-url")
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `returns 400 when host is null`() = runBlocking {
        val response = controller.proxyImage("https:///path-only")
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    // -----------------------------------------------------------------------
    // proxyImage — success
    // -----------------------------------------------------------------------

    @Test
    fun `proxies image from licdn and returns JPEG`() = runBlocking {
        val imageBytes = byteArrayOf(1, 2, 3)
        mockWebClientResponse(imageBytes)

        val response = controller.proxyImage("https://media.licdn.com/media/test.jpg")

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.headers.contentType).isEqualTo(
            org.springframework.http.MediaType.IMAGE_JPEG,
        )
        assertThat(response.body).isEqualTo(imageBytes)
    }

    @Test
    fun `proxies image from twimg and returns JPEG`() = runBlocking {
        val imageBytes = byteArrayOf(4, 5, 6)
        mockWebClientResponse(imageBytes)

        val response = controller.proxyImage("https://pbs.twimg.com/media/test.jpg")

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.headers.contentType).isEqualTo(
            org.springframework.http.MediaType.IMAGE_JPEG,
        )
        assertThat(response.body).isEqualTo(imageBytes)
    }

    @Test
    fun `proxies image from fbsbx and returns PNG`() = runBlocking {
        val imageBytes = byteArrayOf(7, 8, 9)
        mockWebClientResponse(imageBytes)

        val response = controller.proxyImage("https://platform-lookaside.fbsbx.com/media/img.png")

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.headers.contentType).isEqualTo(
            org.springframework.http.MediaType.IMAGE_PNG,
        )
        assertThat(response.body).isEqualTo(imageBytes)
    }

    // -----------------------------------------------------------------------
    // proxyImage — upstream error propagation
    // -----------------------------------------------------------------------

    @Test
    fun `forwards upstream 404 status`() = runBlocking {
        mockWebClientError(404)

        val response = controller.proxyImage("https://media.licdn.com/media/missing.jpg")

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `forwards upstream 502 status`() = runBlocking {
        mockWebClientError(502)

        val response = controller.proxyImage("https://media.licdn.com/media/error.jpg")

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_GATEWAY)
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    private fun mockWebClientResponse(bytes: ByteArray) {
        val spec = Mockito.mock(WebClient.RequestHeadersUriSpec::class.java)
        val headersSpec = Mockito.mock(WebClient.RequestHeadersSpec::class.java)
        val responseSpec = Mockito.mock(WebClient.ResponseSpec::class.java)

        `when`(webClient.get()).thenReturn(spec)
        `when`(spec.uri(any(URI::class.java))).thenReturn(spec)
        `when`(spec.accept(any(), any(), any())).thenReturn(headersSpec)
        `when`(headersSpec.retrieve()).thenReturn(responseSpec)
        `when`(
            responseSpec.bodyToMono(
                any(ParameterizedTypeReference::class.java) as ParameterizedTypeReference<ByteArray>,
            ),
        ).thenReturn(Mono.just(bytes))
    }

    @Suppress("UNCHECKED_CAST")
    private fun mockWebClientError(statusCode: Int) {
        val spec = Mockito.mock(WebClient.RequestHeadersUriSpec::class.java)
        val headersSpec = Mockito.mock(WebClient.RequestHeadersSpec::class.java)
        val responseSpec = Mockito.mock(WebClient.ResponseSpec::class.java)

        `when`(webClient.get()).thenReturn(spec)
        `when`(spec.uri(any(URI::class.java))).thenReturn(spec)
        `when`(spec.accept(any(), any(), any())).thenReturn(headersSpec)
        `when`(headersSpec.retrieve()).thenReturn(responseSpec)
        `when`(
            responseSpec.bodyToMono(
                any(ParameterizedTypeReference::class.java) as ParameterizedTypeReference<ByteArray>,
            ),
        ).thenThrow(
            WebClientResponseException.create(
                statusCode,
                "Upstream error",
                org.springframework.http.HttpHeaders.EMPTY,
                ByteArray(0),
                StandardCharsets.UTF_8,
                null,
            ),
        )
    }
}
