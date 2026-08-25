package com.profiletailors.smp.media.infrastructure.unsplash

import com.profiletailors.smp.media.application.UnsplashPhotoNotFoundException
import com.profiletailors.smp.media.application.UnsplashProviderException
import com.profiletailors.smp.media.application.UnsplashProviderNotConfiguredException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

class UnsplashWebClientTest {
    @Test
    fun `should list editorial photos with auth headers when query is blank`() = runTest {
        val requests = mutableListOf<ClientRequest>()
        val adapter = adapter(requests, EDITORIAL_RESPONSE)

        val photos = adapter.search(null)

        photos.single().externalId shouldBe "photo-1"
        photos.single().previewUrl shouldBe "https://images.unsplash.com/photo-1"
        requests.single().url().toString() shouldContain "/photos?page=1&per_page=20"
        requests.single().headers().getFirst(HttpHeaders.AUTHORIZATION) shouldBe "Client-ID test-key"
        requests.single().headers().getFirst("Accept-Version") shouldBe "v1"
    }

    @Test
    fun `term search uses high content filtering and preserves attribution`() = runTest {
        val requests = mutableListOf<ClientRequest>()
        val adapter = adapter(requests, """{"results":$EDITORIAL_RESPONSE}""")

        val photos = adapter.search("remote work")

        photos.single().authorName shouldBe "Test Author"
        photos.single().sourceUrl.shouldContain("utm_source=profile_tailors")
        photos.single().sourceUrl.shouldContain("utm_medium=referral")
        photos.single().authorUrl.shouldContain("utm_source=profile_tailors")
        requests.single().url().toString().shouldContain("query=remote%20work")
        requests.single().url().toString().shouldContain("content_filter=high")
    }

    @Test
    fun `disabled configuration fails before making a provider request`() = runTest {
        val requests = mutableListOf<ClientRequest>()
        val adapter = adapter(
            requests,
            EDITORIAL_RESPONSE,
            UnsplashProperties(enabled = false, accessKey = ""),
        )

        shouldThrow<UnsplashProviderNotConfiguredException> { adapter.search(null) }
        requests.isEmpty() shouldBe true
    }

    @Test
    fun `download rejects a provider image hosted outside Unsplash`() = runTest {
        val adapter = adapter(mutableListOf(), EDITORIAL_RESPONSE)
        val photo = adapter.search(null).single().copy(importUrl = "https://example.com/photo.jpg")

        shouldThrow<UnsplashProviderException> { adapter.download(photo).toList() }
            .message.shouldContain("unexpected image host")
    }

    @Test
    fun `get photo returns resolved photo when external id is found`() = runTest {
        val requests = mutableListOf<ClientRequest>()
        val adapter = adapter(requests, PHOTO_RESPONSE)

        val photo = adapter.get("photo-1")

        photo.externalId shouldBe "photo-1"
        requests.single().url().toString() shouldContain "/photos/photo-1"
        requests.single().headers().getFirst(HttpHeaders.AUTHORIZATION) shouldBe "Client-ID test-key"
    }

    @Test
    fun `get photo throws photo not found when 404 is returned`() = runTest {
        val requests = mutableListOf<ClientRequest>()
        val adapter = adapter(requests, EDITORIAL_RESPONSE, errorStatus = HttpStatus.NOT_FOUND)

        shouldThrow<UnsplashPhotoNotFoundException> { adapter.get("photo-1") }
            .externalId shouldBe "photo-1"
    }

    @Test
    fun `get photo wraps non-HTTP failures as provider exception`() = runTest {
        val requests = mutableListOf<ClientRequest>()
        val adapter = adapterWithExchangeError(requests)

        shouldThrow<UnsplashProviderException> { adapter.get("photo-1") }
            .message.shouldContain("failed")
    }

    @Test
    fun `track download sends request to download location with auth headers`() = runTest {
        val requests = mutableListOf<ClientRequest>()
        val adapter = adapter(requests, EDITORIAL_RESPONSE)
        val photo = adapter.search(null).single()

        adapter.trackDownload(photo)

        requests.last().url().toString() shouldContain "/photos/photo-1/download"
        requests.last().headers().getFirst(HttpHeaders.AUTHORIZATION) shouldBe "Client-ID test-key"
    }

    @Test
    fun `track download wraps non-HTTP failures as provider exception`() = runTest {
        val requests = mutableListOf<ClientRequest>()
        val photo = adapter(mutableListOf(), EDITORIAL_RESPONSE).search(null).single()
        val adapter = adapterWithExchangeError(requests)

        shouldThrow<UnsplashProviderException> { adapter.trackDownload(photo) }
            .message.shouldContain("failed")
    }

    @Test
    fun `search maps 429 response to rate limit provider exception`() = runTest {
        val requests = mutableListOf<ClientRequest>()
        val adapter = adapter(requests, EDITORIAL_RESPONSE, errorStatus = HttpStatus.TOO_MANY_REQUESTS)

        shouldThrow<UnsplashProviderException> { adapter.search("test") }
            .message.shouldContain("rate limit")
    }

    @Test
    fun `search wraps non-HTTP failures as provider exception`() = runTest {
        val requests = mutableListOf<ClientRequest>()
        val adapter = adapterWithExchangeError(requests)

        shouldThrow<UnsplashProviderException> { adapter.search("test") }
            .message.shouldContain("failed")
    }

    @Test
    fun `download wraps non-HTTP failures as provider exception`() = runTest {
        val requests = mutableListOf<ClientRequest>()
        val adapter = adapter(mutableListOf(), EDITORIAL_RESPONSE)
        val photo = adapter.search(null).single().copy(
            importUrl = "https://images.unsplash.com/photo-download-regular",
        )
        val downloadAdapter = adapterWithDownloadError(requests, photo.importUrl)

        shouldThrow<UnsplashProviderException> { downloadAdapter.download(photo).toList() }
    }

    @Test
    fun `download rejects insecure image URL`() = runTest {
        val adapter = adapter(mutableListOf(), EDITORIAL_RESPONSE)
        val photo = adapter.search(null).single().copy(importUrl = "http://images.unsplash.com/photo-1.jpg")

        shouldThrow<UnsplashProviderException> { adapter.download(photo).toList() }
            .message.shouldContain("insecure image URL")
    }

    private fun adapter(
        requests: MutableList<ClientRequest>,
        responseBody: String,
        properties: UnsplashProperties = UnsplashProperties(enabled = true, accessKey = "test-key"),
        errorStatus: HttpStatus? = null,
    ): UnsplashWebClient {
        val exchange = ExchangeFunction { request ->
            requests += request
            val builder = ClientResponse.create(if (errorStatus != null) errorStatus else HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(responseBody)
            Mono.just(builder.build())
        }
        return UnsplashWebClient(WebClient.builder().exchangeFunction(exchange).build(), properties)
    }

    private fun adapterWithExchangeError(requests: MutableList<ClientRequest>): UnsplashWebClient {
        val exchange = ExchangeFunction { request ->
            requests += request
            Mono.error(IllegalStateException("connection refused"))
        }
        return UnsplashWebClient(
            WebClient.builder().exchangeFunction(exchange).build(),
            UnsplashProperties(enabled = true, accessKey = "test-key"),
        )
    }

    private fun adapterWithDownloadError(
        requests: MutableList<ClientRequest>,
        downloadUrl: String,
    ): UnsplashWebClient {
        val exchange = ExchangeFunction { request ->
            requests += request
            if (request.url().toString().contains(downloadUrl)) {
                Mono.error(IllegalStateException("download failed"))
            } else {
                Mono.just(
                    ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .body(EDITORIAL_RESPONSE)
                        .build(),
                )
            }
        }
        return UnsplashWebClient(
            WebClient.builder().exchangeFunction(exchange).build(),
            UnsplashProperties(enabled = true, accessKey = "test-key"),
        )
    }

    private companion object {
        val PHOTO_RESPONSE =
            """
              {
                "id":"photo-1",
                "description":null,
                "alt_description":"Remote team working",
                "urls":{
                  "small":"https://images.unsplash.com/photo-1",
                  "regular":"https://images.unsplash.com/photo-1-regular"
                },
                "links":{
                  "html":"https://unsplash.com/photos/photo-1",
                  "download_location":"https://api.unsplash.com/photos/photo-1/download"
                },
                "user":{
                  "name":"Test Author",
                  "links":{"html":"https://unsplash.com/@test-author"}
                }
              }
            """.trimIndent()

        val EDITORIAL_RESPONSE =
            """[
              {
                "id":"photo-1",
                "description":null,
                "alt_description":"Remote team working",
                "urls":{
                  "small":"https://images.unsplash.com/photo-1",
                  "regular":"https://images.unsplash.com/photo-1-regular"
                },
                "links":{
                  "html":"https://unsplash.com/photos/photo-1",
                  "download_location":"https://api.unsplash.com/photos/photo-1/download"
                },
                "user":{
                  "name":"Test Author",
                  "links":{"html":"https://unsplash.com/@test-author"}
                }
              }
            ]
            """.trimIndent()
    }
}
