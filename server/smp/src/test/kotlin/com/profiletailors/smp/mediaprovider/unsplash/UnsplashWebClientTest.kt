package com.profiletailors.smp.mediaprovider.unsplash

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class UnsplashWebClientTest {

    @Test
    fun `searchPhotos parses Unsplash JSON response`() = runTest {
        val payload = """
            {
              "total": 1,
              "totalPages": 1,
              "results": [
                {
                  "id": "abc",
                  "width": 800,
                  "height": 600,
                  "color": "#fff",
                  "altDescription": "mountain",
                  "urls": {"thumb": "https://images.example/thumb", "full": "https://images.example/full"},
                  "links": {"html": "https://unsplash.com/photos/abc", "download": "https://api.unsplash.com/photos/abc/download"},
                  "user": {"name": "Alice", "links": {"html": "https://unsplash.com/@alice"}},
                  "tags": [{"title": "nature"}]
                }
              ]
            }
        """.trimIndent()
        val client = UnsplashWebClient(
            webClient = jsonWebClient(jsonExchange(payload)),
            properties = props(),
        )

        val response = client.searchPhotos(query = "mountain", page = 2)

        assertEquals(1, response.total)
        assertEquals("abc", response.results.single().id)
        assertEquals("Alice", response.results.single().user.name)
        assertEquals("https://unsplash.com/@alice", response.results.single().user.links.html)
    }

    @Test
    fun `searchPhotos adds Authorization header with access key`() = runTest {
        var lastAuth: String? = null
        val exchange = ExchangeFunction {
            lastAuth = it.headers()[HttpHeaders.AUTHORIZATION]?.firstOrNull()
            Mono.just(
                ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("""{"total":0,"totalPages":0,"results":[]}""")
                    .build(),
            )
        }
        val client = UnsplashWebClient(
            webClient = WebClient.builder().exchangeFunction(exchange).build(),
            properties = props(accessKey = "secret-key-789"),
        )

        client.searchPhotos(query = "q", page = 1)

        assertEquals("Client-ID secret-key-789", lastAuth)
    }

    @Test
    fun `searchPhotos maps 4xx to ProviderErrorException`() = runTest {
        val client = UnsplashWebClient(
            webClient = WebClient.builder()
                .exchangeFunction(
                    ExchangeFunction {
                        Mono.just(
                            ClientResponse.create(HttpStatus.UNAUTHORIZED)
                                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                .body("""{"errors":["bad key"]}""")
                                .build(),
                        )
                    },
                )
                .build(),
            properties = props(),
        )

        val error = kotlin.runCatching { client.searchPhotos(query = "x", page = 1) }.exceptionOrNull()
        assertTrue(error is ProviderErrorException)
    }

    @Test
    fun `searchPhotos captures 429 Retry-After header`() = runTest {
        val client = UnsplashWebClient(
            webClient = WebClient.builder()
                .exchangeFunction(
                    ExchangeFunction {
                        Mono.just(
                            ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS)
                                .header("Retry-After", "5")
                                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                .body("")
                                .build(),
                        )
                    },
                )
                .build(),
            properties = props(),
        )

        val error = kotlin.runCatching { client.searchPhotos(query = "x", page = 1) }.exceptionOrNull()
        assertTrue(error is UnsplashRateLimitedException)
        assertEquals(5, (error as UnsplashRateLimitedException).retryAfterSeconds)
    }

    @Test
    fun `searchPhotos falls back to default Retry-After when header is absent`() = runTest {
        val client = UnsplashWebClient(
            webClient = WebClient.builder()
                .exchangeFunction(
                    ExchangeFunction {
                        Mono.just(
                            ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS)
                                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                .body("")
                                .build(),
                        )
                    },
                )
                .build(),
            properties = props(),
        )

        val error = kotlin.runCatching { client.searchPhotos(query = "x", page = 1) }.exceptionOrNull()
        assertTrue(error is UnsplashRateLimitedException)
        assertEquals(5, (error as UnsplashRateLimitedException).retryAfterSeconds)
    }

    @Test
    fun `searchPhotos maps timeout to ProviderUnavailableException`() = runTest {
        val client = UnsplashWebClient(
            webClient = WebClient.builder()
                .exchangeFunction(ExchangeFunction { Mono.error(java.util.concurrent.TimeoutException("slow")) })
                .build(),
            properties = props(),
        )

        val error = kotlin.runCatching { client.searchPhotos(query = "x", page = 1) }.exceptionOrNull()
        assertTrue(error is ProviderUnavailableException)
    }

    @Test
    fun `getPhoto rejects blank ids`() = runTest {
        val client = UnsplashWebClient(
            webClient = WebClient.builder().exchangeFunction(jsonExchange("{}")).build(),
            properties = props(),
        )

        val error = kotlin.runCatching { client.getPhoto("   ") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `getPhoto parses Unsplash JSON response`() = runTest {
        val payload = """
            {
              "id": "photo-1",
              "width": 640,
              "height": 480,
              "color": "#fff",
              "altDescription": "river",
              "urls": {"thumb": "https://images.example/thumb", "full": "https://images.example/full"},
              "links": {"html": "https://unsplash.com/photos/photo-1", "download": "https://api.unsplash.com/photos/photo-1/download"},
              "user": {"name": "Alice", "links": {"html": "https://unsplash.com/@alice"}},
              "tags": []
            }
        """.trimIndent()
        val client = UnsplashWebClient(
            webClient = jsonWebClient(jsonExchange(payload)),
            properties = props(),
        )

        val photo = client.getPhoto("photo-1")

        assertEquals("photo-1", photo.id)
        assertEquals("Alice", photo.user.name)
    }

    @Test
    fun `getPhoto maps timeout to ProviderUnavailableException`() = runTest {
        val client = UnsplashWebClient(
            webClient = WebClient.builder()
                .exchangeFunction(ExchangeFunction { Mono.error(java.util.concurrent.TimeoutException("slow")) })
                .build(),
            properties = props(),
        )

        val error = kotlin.runCatching { client.getPhoto("photo-1") }.exceptionOrNull()
        assertTrue(error is ProviderUnavailableException)
    }

    @Test
    fun `downloadPhoto streams body bytes and reports Content-Type`() = runTest {
        val bytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val exchange = ExchangeFunction {
            Mono.just(
                ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, "image/png")
                    .header(HttpHeaders.CONTENT_LENGTH, bytes.size.toString())
                    .body(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(bytes)))
                    .build(),
            )
        }
        val client = UnsplashWebClient(
            webClient = WebClient.builder().exchangeFunction(exchange).build(),
            properties = props(),
        )

        val binary = client.downloadPhoto(minimalPhoto("download-me"))

        val collected = binary.bytes.toList()
        assertEquals(1, collected.size)
        assertEquals(bytes.size.toLong(), binary.contentLength)
        assertEquals("image/png", binary.mediaType)
        assertNotNull(binary)
    }

    @Test
    fun `downloadPhoto maps timeout to ProviderUnavailableException`() = runTest {
        val exchange = ExchangeFunction {
            Mono.error(java.util.concurrent.TimeoutException("connect timeout"))
        }
        val client = UnsplashWebClient(
            webClient = WebClient.builder().exchangeFunction(exchange).build(),
            properties = props(),
        )

        val error = kotlin.runCatching { client.downloadPhoto(minimalPhoto("slow")) }.exceptionOrNull()
        assertTrue(error is ProviderUnavailableException)
    }

    @Test
    fun `downloadPhoto falls back to full url when download link is blank`() = runTest {
        var requestedUrl: String? = null
        val exchange = ExchangeFunction { request ->
            requestedUrl = request.url().toString()
            Mono.just(
                ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, "image/jpeg")
                    .header(HttpHeaders.CONTENT_LENGTH, "2")
                    .body(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(byteArrayOf(1, 2))))
                    .build(),
            )
        }
        val client = UnsplashWebClient(
            webClient = WebClient.builder().exchangeFunction(exchange).build(),
            properties = props(),
        )

        client.downloadPhoto(
            minimalPhoto("fallback").copy(
                links = UnsplashPhotoLinks("https://unsplash.com/photos/fallback", ""),
            ),
        )

        assertTrue(requestedUrl?.contains("https://images.unsplash.com/fallback?w=2048") == true)
    }

    @Test
    fun `downloadPhoto maps 5xx to ProviderUnavailableException`() = runTest {
        val client = UnsplashWebClient(
            webClient = WebClient.builder()
                .exchangeFunction(
                    ExchangeFunction {
                        Mono.just(ClientResponse.create(HttpStatus.BAD_GATEWAY).build())
                    },
                )
                .build(),
            properties = props(),
        )

        val error = kotlin.runCatching { client.downloadPhoto(minimalPhoto("broken")) }.exceptionOrNull()
        assertTrue(error is ProviderUnavailableException)
    }

    private fun jsonWebClient(exchange: ExchangeFunction, expectedPathSuffix: String? = null): WebClient {
        var lastPath: String? = null
        val wrapper = ExchangeFunction { request ->
            lastPath = request.url().path
            if (expectedPathSuffix != null) {
                require(request.url().path.endsWith(expectedPathSuffix)) {
                    "expected path suffix $expectedPathSuffix, got ${request.url().path}"
                }
            }
            exchange.exchange(request)
        }
        return WebClient.builder()
            .exchangeFunction(wrapper)
            .build()
            .also {
                assertFalse(
                    expectedPathSuffix != null && lastPath != null && !lastPath.endsWith(expectedPathSuffix),
                )
            }
    }

    private fun jsonExchange(body: String): ExchangeFunction = ExchangeFunction {
        it.headers().accept.forEach { accept -> assertTrue(accept.toString().contains("json")) }
        Mono.just(
            ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build(),
        )
    }

    private fun minimalPhoto(id: String) = UnsplashPhoto(
        id = id,
        width = 100,
        height = 100,
        color = null,
        altDescription = null,
        urls = UnsplashPhotoUrls("https://images.unsplash.com/$id?w=200", "https://images.unsplash.com/$id?w=2048"),
        links = UnsplashPhotoLinks("https://unsplash.com/photos/$id", "https://api.unsplash.com/photos/$id/download"),
        user = UnsplashUser("Creator", UnsplashUserLinks("https://unsplash.com/@creator")),
        tags = emptyList(),
    )

    private fun props(accessKey: String = "test-access-key"): UnsplashProperties = UnsplashProperties(
        enabled = true,
        accessKey = accessKey,
        baseUrl = "https://api.unsplash.com",
        timeout = java.time.Duration.ofSeconds(5),
        pageSize = 20,
    )
}
