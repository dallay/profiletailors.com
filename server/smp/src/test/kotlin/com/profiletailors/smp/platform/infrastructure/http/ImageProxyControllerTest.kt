package com.profiletailors.smp.platform.infrastructure.http

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking

class ImageProxyControllerTest {

    private fun buildController(exchangeFn: ExchangeFunction): ImageProxyController {
        val webClient = WebClient.builder().exchangeFunction(exchangeFn).build()
        return ImageProxyController(webClient)
    }

    // -----------------------------------------------------------------------
    // allowedHosts
    // -----------------------------------------------------------------------

    @Test
    fun `allows licdn com host`() {
        val controller = buildController(okExchange(MediaType.IMAGE_JPEG, byteArrayOf()))
        assertThat(controller.allowedHosts).contains("media.licdn.com")
    }

    @Test
    fun `allows twimg com host`() {
        val controller = buildController(okExchange(MediaType.IMAGE_JPEG, byteArrayOf()))
        assertThat(controller.allowedHosts).contains("pbs.twimg.com")
    }

    @Test
    fun `allows facebook cdn hosts`() {
        val controller = buildController(okExchange(MediaType.IMAGE_JPEG, byteArrayOf()))
        assertThat(controller.allowedHosts)
            .contains("platform-lookaside.fbsbx.com", "scontent.xx.fbcdn.net")
    }

    @Test
    fun `allows instagram cdn host`() {
        val controller = buildController(okExchange(MediaType.IMAGE_JPEG, byteArrayOf()))
        assertThat(controller.allowedHosts)
            .contains("instagram.fbog1-1.fna.fbcdn.net")
    }

    @Test
    fun `does not allow arbitrary hosts`() {
        val controller = buildController(okExchange(MediaType.IMAGE_JPEG, byteArrayOf()))
        assertThat(controller.allowedHosts)
            .doesNotContain("evil.com", "media.evil.com", "licdn.com", "example.com")
    }

    // -----------------------------------------------------------------------
    // proxyImage — validation (no HTTP call made)
    // -----------------------------------------------------------------------

    @Test
    fun `returns 400 when host is not in allowed list`() = runBlocking<Unit> {
        val controller = buildController(shouldNotBeCalled())
        val response = controller.proxyImage("https://evil.com/image.jpg")
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `returns 400 when URL has no host`() = runBlocking<Unit> {
        val controller = buildController(shouldNotBeCalled())
        val response = controller.proxyImage("not-a-valid-url")
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `returns 400 when host is null`() = runBlocking<Unit> {
        val controller = buildController(shouldNotBeCalled())
        val response = controller.proxyImage("https:///path-only")
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `rejects malformed URLs with 400`() = runBlocking<Unit> {
        val controller = buildController(shouldNotBeCalled())
        val response = controller.proxyImage("not a valid url %%%")
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `rejects http scheme`() = runBlocking<Unit> {
        val controller = buildController(shouldNotBeCalled())
        val response = controller.proxyImage("http://media.licdn.com/media/test.jpg")
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `matches host case-insensitively`() = runBlocking<Unit> {
        val imageBytes = byteArrayOf(1, 2, 3)
        val controller = buildController(okExchange(MediaType.IMAGE_JPEG, imageBytes))
        val response = controller.proxyImage("https://MEDIA.LICDN.COM/media/test.jpg")
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    }

    // -----------------------------------------------------------------------
    // proxyImage — success
    // -----------------------------------------------------------------------

    @Test
    fun `proxies image and returns upstream content type`() = runBlocking<Unit> {
        val imageBytes = byteArrayOf(1, 2, 3)
        val controller = buildController(okExchange(MediaType.IMAGE_GIF, imageBytes))
        val response = controller.proxyImage("https://media.licdn.com/media/test.jpg")
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.headers.contentType).isEqualTo(MediaType.IMAGE_GIF)
        assertThat(response.body).isEqualTo(imageBytes)
    }

    @Test
    fun `proxies image from twimg with upstream content type`() = runBlocking<Unit> {
        val imageBytes = byteArrayOf(4, 5, 6)
        val controller = buildController(okExchange(MediaType.IMAGE_PNG, imageBytes))
        val response = controller.proxyImage("https://pbs.twimg.com/media/test.png")
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.headers.contentType).isEqualTo(MediaType.IMAGE_PNG)
        assertThat(response.body).isEqualTo(imageBytes)
    }

    @Test
    fun `proxies image from fbsbx with upstream content type`() = runBlocking<Unit> {
        val imageBytes = byteArrayOf(7, 8, 9)
        val controller = buildController(okExchange(MediaType.IMAGE_JPEG, imageBytes))
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
        val controller = buildController(errorExchange(404))
        val response = controller.proxyImage("https://media.licdn.com/media/missing.jpg")
        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `forwards upstream 502 status`() = runBlocking<Unit> {
        val controller = buildController(errorExchange(502))
        val response = controller.proxyImage("https://media.licdn.com/media/error.jpg")
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_GATEWAY)
    }

    // -----------------------------------------------------------------------
    // Helpers — real WebClient with mock ExchangeFunction
    // -----------------------------------------------------------------------

    private fun okExchange(contentType: MediaType, body: ByteArray): ExchangeFunction {
        val buffer = DefaultDataBufferFactory.sharedInstance.wrap(body)
        return ExchangeFunction {
            Mono.just(
                ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, contentType.toString())
                    .body(Flux.just(buffer))
                    .build(),
            )
        }
    }

    private fun errorExchange(statusCode: Int): ExchangeFunction =
        ExchangeFunction {
            Mono.just(
                ClientResponse.create(HttpStatus.valueOf(statusCode))
                    .body(Flux.empty())
                    .build(),
            )
        }

    private fun shouldNotBeCalled(): ExchangeFunction = ExchangeFunction {
        throw AssertionError("ExchangeFunction should not be called for validation-only paths")
    }
}
