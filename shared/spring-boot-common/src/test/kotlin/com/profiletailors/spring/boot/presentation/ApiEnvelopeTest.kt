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
}
