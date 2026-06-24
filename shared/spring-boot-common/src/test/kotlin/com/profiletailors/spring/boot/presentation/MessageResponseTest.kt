package com.profiletailors.spring.boot.presentation

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.time.Instant

internal class MessageResponseTest {

    @Test
    fun `should create response with message`() {
        val response = MessageResponse("Operation completed successfully")

        response.message shouldBe "Operation completed successfully"
    }

    @Test
    fun `should generate a timestamp by default`() {
        val response = MessageResponse("test")

        response.timestamp shouldNotBe null
    }

    @Test
    fun `should generate a recent timestamp`() {
        val before = Instant.now()
        val response = MessageResponse("test")
        val after = Instant.now()

        (response.timestamp >= before) shouldBe true
        (response.timestamp <= after) shouldBe true
    }
}
