package com.profiletailors.smp.media.infrastructure.unsplash

import com.profiletailors.smp.media.application.UnsplashProviderException
import com.profiletailors.smp.media.application.UnsplashProviderNotConfiguredException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

class UnsplashWebClientAdapterTest {
    @Test
    fun `blank search lists editorial photos with authentication headers`() = runTest {
        val requests = mutableListOf<ClientRequest>()
        val adapter = adapter(requests, EDITORIAL_RESPONSE)

        val photos = adapter.search(null)

        assertEquals("photo-1", photos.single().externalId)
        assertEquals("https://images.unsplash.com/photo-1", photos.single().previewUrl)
        assertEquals("/photos?page=1&per_page=20", requests.single().url().toString())
        assertEquals("Client-ID test-key", requests.single().headers().getFirst(HttpHeaders.AUTHORIZATION))
        assertEquals("v1", requests.single().headers().getFirst("Accept-Version"))
    }

    @Test
    fun `term search uses high content filtering and preserves attribution`() = runTest {
        val requests = mutableListOf<ClientRequest>()
        val adapter = adapter(requests, """{"results":$EDITORIAL_RESPONSE}""")

        val photos = adapter.search("remote work")

        assertEquals("Test Author", photos.single().authorName)
        assertTrue(photos.single().sourceUrl.contains("utm_source=profile_tailors"))
        assertTrue(photos.single().sourceUrl.contains("utm_medium=referral"))
        assertTrue(photos.single().authorUrl.contains("utm_source=profile_tailors"))
        assertTrue(requests.single().url().toString().contains("query=remote%20work"))
        assertTrue(requests.single().url().toString().contains("content_filter=high"))
    }

    @Test
    fun `disabled configuration fails before making a provider request`() = runTest {
        val requests = mutableListOf<ClientRequest>()
        val adapter = adapter(
            requests,
            EDITORIAL_RESPONSE,
            UnsplashProperties(enabled = false, accessKey = ""),
        )

        assertThrows<UnsplashProviderNotConfiguredException> { adapter.search(null) }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `download rejects a provider image hosted outside Unsplash`() = runTest {
        val adapter = adapter(mutableListOf(), EDITORIAL_RESPONSE)
        val photo = adapter.search(null).single().copy(importUrl = "https://example.com/photo.jpg")

        assertThrows<UnsplashProviderException> { adapter.download(photo).toList() }
    }

    private fun adapter(
        requests: MutableList<ClientRequest>,
        responseBody: String,
        properties: UnsplashProperties = UnsplashProperties(enabled = true, accessKey = "test-key"),
    ): UnsplashWebClientAdapter {
        val exchange = ExchangeFunction { request ->
            requests += request
            Mono.just(
                ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(responseBody)
                    .build(),
            )
        }
        return UnsplashWebClientAdapter(WebClient.builder().exchangeFunction(exchange).build(), properties)
    }

    private companion object {
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
