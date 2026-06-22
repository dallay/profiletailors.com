package com.profiletailors.storage.infrastructure

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.core.io.buffer.DataBuffer
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
        val flux: Flux<DataBuffer> = Flux.fromIterable(data).map { factory.wrap(it.toByteArray()) }

        val flow = flux.asFlow()

        val results = flow.toList()
        results.size shouldBe 2
        String(results[0]) shouldBe "hello"
        String(results[1]) shouldBe "world"
    }

    @Test
    fun `should convert empty Flow to empty Flux`() = runTest {
        val emptyFlow = emptyList<ByteArray>().asFlow()

        val flux = emptyFlow.asFlux()

        val results = flux.collectList().block()!!
        results.size shouldBe 0
    }

    @Test
    fun `should convert empty Flux to empty Flow`() = runTest {
        val emptyFlux: Flux<DataBuffer> = Flux.empty() 

        val flow = emptyFlux.asFlow()

        val results = flow.toList()
        results.size shouldBe 0
    }

    @Test
    fun `should preserve byte content through Flow to Flux conversion`() = runTest {
        val binaryData = byteArrayOf(0x00, 0x01, 0x02, 0xFF.toByte())
        val flow = listOf(binaryData).asFlow()

        val flux = flow.asFlux()

        val results = flux.collectList().block()!!
        results.size shouldBe 1
        val bytes = ByteArray(results[0].readableByteCount())
        results[0].read(bytes)
        bytes[0] shouldBe 0x00.toByte()
        bytes[3] shouldBe 0xFF.toByte()
    }

    @Test
    fun `should convert single-element Flow to single-element Flux`() = runTest {
        val data = listOf("single".toByteArray())
        val flow = data.asFlow()

        val flux = flow.asFlux()
        val results = flux.collectList().block()!!

        results.size shouldBe 1
        results[0].toString(java.nio.charset.StandardCharsets.UTF_8) shouldBe "single"
    }
}
