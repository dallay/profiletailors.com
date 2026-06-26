package com.profiletailors.storage.infrastructure

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.asPublisher
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import reactor.core.publisher.Flux

// Simple adapters - conversion utilities between kotlinx.coroutines Flow and Reactor Flux of DataBuffer

fun Flow<ByteArray>.asFlux(): Flux<DataBuffer> {
    val factory = DefaultDataBufferFactory()
    return Flux.from(this.asPublisher()).map { bytes -> factory.wrap(bytes) }
}

fun Flux<DataBuffer>.asFlow(): Flow<ByteArray> = this.map { buffer ->
    try {
        val bytes = ByteArray(buffer.readableByteCount())
        buffer.read(bytes)
        bytes
    } finally {
        DataBufferUtils.release(buffer)
    }
}.asFlow()
