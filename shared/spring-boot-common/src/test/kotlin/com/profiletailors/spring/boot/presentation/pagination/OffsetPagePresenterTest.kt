package com.profiletailors.spring.boot.presentation.pagination

import com.profiletailors.common.domain.presentation.pagination.OffsetPageResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.springframework.core.MethodParameter
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.reactive.HandlerResult
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper

class OffsetPagePresenterTest {

    private val objectMapper = ObjectMapper()
    private val presenter = OffsetPagePresenter(objectMapper)

    /* stub to obtain a MethodParameter for HandlerResult construction */
    @Suppress("unused")
    private fun stubMethod(): Mono<OffsetPageResponse<String>> = Mono.empty()

    private val stubMethodParam: MethodParameter = run {
        val method = this::class.java.getDeclaredMethod("stubMethod")
        MethodParameter.forExecutable(method, -1) // -1 = return type
    }

    @Test
    fun `should set pagination headers when all fields are present`() = runBlocking {
        val exchange = exchangeFor("/items")
        val page = OffsetPageResponse(data = listOf("a", "b"), total = 42L, perPage = 20, page = 2, totalPages = 3)
        val result = HandlerResult(this@OffsetPagePresenterTest, Mono.just(page), stubMethodParam)

        presenter.present(exchange, result)

        with(exchange.response.headers) {
            assertEquals("42", getFirst("Total-Count"))
            assertEquals("2", getFirst("Page"))
            assertEquals("20", getFirst("Per-Page"))
            assertEquals(MediaType.APPLICATION_JSON_VALUE, contentType.toString())
        }
    }

    @Test
    fun `should merge access control expose headers`() = runBlocking {
        val exchange = exchangeFor("/items")
        exchange.response.headers.add(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "X-Custom")
        val page = OffsetPageResponse(data = listOf("x"), perPage = 10, total = 1L, page = 1)
        val result = HandlerResult(this@OffsetPagePresenterTest, Mono.just(page), stubMethodParam)

        presenter.present(exchange, result)

        val exposed = exchange.response.headers.getFirst(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS)!!
        assertEquals(setOf("X-Custom", "Total-Count", "Page", "Per-Page"), exposed.split(", ").toSet())
    }

    @Test
    fun `should not set optional headers when null`() = runBlocking {
        val exchange = exchangeFor("/items")
        val page = OffsetPageResponse(data = listOf("only"), total = null, perPage = 20, page = null)
        val result = HandlerResult(this@OffsetPagePresenterTest, Mono.just(page), stubMethodParam)

        presenter.present(exchange, result)

        with(exchange.response.headers) {
            assertNull(getFirst("Total-Count"))
            assertNull(getFirst("Page"))
            assertEquals("20", getFirst("Per-Page"))
        }
    }

    @Test
    fun `should skip gracefully when return value is not a Mono`() = runBlocking {
        val exchange = exchangeFor("/items")
        val result = HandlerResult(this@OffsetPagePresenterTest, "not-a-mono", stubMethodParam)

        presenter.present(exchange, result)

        assertNull(exchange.response.headers.getFirst("Per-Page"))
    }

    @Test
    fun `should skip gracefully when mono completes empty`() = runBlocking {
        val exchange = exchangeFor("/items")
        val result = HandlerResult(
            this@OffsetPagePresenterTest,
            Mono.empty<OffsetPageResponse<String>>(),
            stubMethodParam,
        )

        presenter.present(exchange, result)

        assertNull(exchange.response.headers.getFirst("Per-Page"))
    }

    @Test
    fun `should write data as json in response body`() = runBlocking {
        val exchange = exchangeFor("/items")
        val data = listOf("hello", "world")
        val page = OffsetPageResponse(data = data, total = 2L, perPage = 20)
        val result = HandlerResult(this@OffsetPagePresenterTest, Mono.just(page), stubMethodParam)

        presenter.present(exchange, result)

        val expectedJson = objectMapper.writeValueAsBytes(data)
        val actualJson = exchange.response.getBody()
            .reduce { a, b -> a.write(b) }
            .block()!!
            .asByteBuffer()
            .let { buffer -> ByteArray(buffer.remaining()).also { buffer.get(it) } }

        assertEquals(expectedJson.toList(), actualJson.toList())
    }

    private fun exchangeFor(path: String): MockServerWebExchange =
        MockServerWebExchange.from(MockServerHttpRequest.get(path).build())
}
