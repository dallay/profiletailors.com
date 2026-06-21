package com.profiletailors.spring.boot.presentation

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.time.Instant

class ApiEnvelopeTest {

    @Test
    fun `should create envelope with required message`() {
        val envelope = ApiEnvelope<String>("Success")

        envelope.message shouldBe "Success"
        envelope.data shouldBe null
        envelope.timestamp shouldNotBe null
    }

    @Test
    fun `should create envelope with message and data`() {
        val data = "test data"
        val envelope = ApiEnvelope("Success", data)

        envelope.message shouldBe "Success"
        envelope.data shouldBe data
    }

    @Test
    fun `should use custom timestamp if provided`() {
        val timestamp = Instant.parse("2025-01-01T00:00:00Z")
        val envelope = ApiEnvelope("Success", null, timestamp)

        envelope.timestamp shouldBe timestamp
    }

    @Test
    fun `should work with complex data type`() {
        data class Payload(val id: Int, val name: String)
        val payload = Payload(1, "test")
        val envelope = ApiEnvelope("Created", payload)

        envelope.data shouldBe payload
        envelope.data?.id shouldBe 1
    }

    @Test
    fun `should generate a recent timestamp by default`() {
        val before = Instant.now()
        val envelope = ApiEnvelope<String>("test")
        val after = Instant.now()

        (envelope.timestamp >= before) shouldBe true
        (envelope.timestamp <= after) shouldBe true
    }

    @Test
    fun `should create envelope with list data`() {
        val items = listOf("a", "b", "c")
        val envelope = ApiEnvelope("Found items", items)

        envelope.data shouldBe items
        envelope.data?.size shouldBe 3
    }
}
