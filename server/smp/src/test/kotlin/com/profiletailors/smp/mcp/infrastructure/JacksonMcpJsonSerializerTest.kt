package com.profiletailors.smp.mcp.infrastructure

import com.fasterxml.jackson.core.JsonProcessingException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.time.Instant

class JacksonMcpJsonSerializerTest {
    private val serializer = JacksonMcpJsonSerializer()

    @Test
    fun `default serializer round trips Kotlin data and Java time`() {
        val expected = SerializedToolResult(
            publicationId = "publication-1",
            occurredAt = Instant.parse("2026-09-06T12:34:56Z"),
        )

        val json = serializer.toJson(expected)

        json shouldContain "\"occurredAt\":\"2026-09-06T12:34:56Z\""
        serializer.fromJson(json, SerializedToolResult::class.java) shouldBe expected
    }

    @Test
    fun `malformed cached JSON is rejected`() {
        shouldThrow<JsonProcessingException> {
            serializer.fromJson("{not-json", SerializedToolResult::class.java)
        }
    }
}

data class SerializedToolResult(val publicationId: String, val occurredAt: Instant)
