package com.profiletailors.storage.infrastructure

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import reactor.core.publisher.Flux

class FlowReactorAdaptersTest {

    private val factory = DefaultDataBufferFactory()

    @Test
    fun `should convert Flow to Flux`() = runTest {
        val data = listOf("hello".toByteArray(), "world".toByteArray())
        val flow = data.asFlow()

        val flux = flow.asFlux()

        val results = flux.collectList().block()!!
        results.size shouldBe 2
        results[0].toString(java.nio.charset.StandardCharsets.UTF_8) shouldBe "hello"
        results[1].toString(java.nio.charset.StandardCharsets.UTF_8) shouldBe "world"
    }

    @Test
    fun `should convert Flux to Flow`() = runTest {
        val data = listOf("hello", "world")
        val flux = Flux.fromIterable(data).map { factory.wrap(it.toByteArray()) }

        val flow = flux.asFlow()

        val results = flow.toList()
        results.size shouldBe 2
        String(results[0], Charsets.UTF_8) shouldBe "hello"
        String(results[1], Charsets.UTF_8) shouldBe "world"
    }
}
