package com.profiletailors.spring.boot.presentation.pagination

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class OffsetApiResponseTest {

    @Test
    fun `should create response with all pagination fields`() {
        val response = OffsetApiResponse(
            data = listOf("a", "b", "c"),
            total = 100L,
            perPage = 20,
            page = 1,
            totalPages = 5,
        )

        response.data shouldBe listOf("a", "b", "c")
        response.total shouldBe 100L
        response.perPage shouldBe 20
        response.page shouldBe 1
        response.totalPages shouldBe 5
        response.message shouldBe "Operation successful"
    }

    @Test
    fun `should use default message when not provided`() {
        val response = OffsetApiResponse(data = emptyList<String>(), perPage = 10)

        response.message shouldBe "Operation successful"
    }

    @Test
    fun `should allow nullable fields to be null`() {
        val response = OffsetApiResponse(data = listOf("item"), perPage = 10)

        response.total shouldBe null
        response.page shouldBe null
        response.totalPages shouldBe null
    }

    @Test
    fun `should map data using inline function`() {
        val original = OffsetApiResponse(
            data = listOf("hello", "world"),
            total = 2L,
            perPage = 10,
            page = 1,
            totalPages = 1,
        )

        val mapped = original.map { it.map(String::uppercase) }

        mapped.data shouldBe listOf("HELLO", "WORLD")
        mapped.total shouldBe 2L
        mapped.perPage shouldBe 10
        mapped.page shouldBe 1
        mapped.totalPages shouldBe 1
    }

    @Test
    fun `should map to different type`() {
        val original = OffsetApiResponse(
            data = listOf(1, 2),
            perPage = 50,
        )

        val mapped: OffsetApiResponse<String> = original.map { numbers ->
            numbers.map { "n-$it" }
        }

        mapped.data shouldBe listOf("n-1", "n-2")
        mapped.perPage shouldBe 50
    }

    @Test
    fun `should preserve nullable fields after map`() {
        val original = OffsetApiResponse(
            data = listOf("x"),
            total = null,
            perPage = 10,
            page = null,
            totalPages = null,
        )

        val mapped = original.map { it }

        mapped.total shouldBe null
        mapped.page shouldBe null
        mapped.totalPages shouldBe null
    }

    @Test
    fun `should preserve message after map`() {
        val original = OffsetApiResponse(
            data = listOf("test"),
            perPage = 25,
            message = "Custom offset message",
        )

        val mapped = original.map { it }

        mapped.message shouldBe "Custom offset message"
    }
}
