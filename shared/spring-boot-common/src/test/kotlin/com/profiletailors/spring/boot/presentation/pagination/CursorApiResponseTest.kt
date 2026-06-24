package com.profiletailors.spring.boot.presentation.pagination

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class CursorApiResponseTest {

    @Test
    fun `should create response with data and pagination cursors`() {
        val response = CursorApiResponse(
            data = listOf("a", "b"),
            prevPageCursor = "cursor-abc",
            nextPageCursor = "cursor-xyz",
        )

        response.data shouldBe listOf("a", "b")
        response.prevPageCursor shouldBe "cursor-abc"
        response.nextPageCursor shouldBe "cursor-xyz"
        response.message shouldBe "Operation successful"
    }

    @Test
    fun `should use default message when not provided`() {
        val response = CursorApiResponse(
            data = listOf(1, 2, 3),
            prevPageCursor = null,
            nextPageCursor = null,
        )

        response.message shouldBe "Operation successful"
    }

    @Test
    fun `should support null cursors`() {
        val response = CursorApiResponse(
            data = listOf("single"),
            prevPageCursor = null,
            nextPageCursor = null,
        )

        response.prevPageCursor shouldBe null
        response.nextPageCursor shouldBe null
    }

    @Test
    fun `should map data using inline function`() {
        val original = CursorApiResponse(
            data = listOf("hello", "world"),
            prevPageCursor = "prev",
            nextPageCursor = "next",
        )

        val mapped = original.map { it.map(String::uppercase) }

        mapped.data shouldBe listOf("HELLO", "WORLD")
        mapped.prevPageCursor shouldBe "prev"
        mapped.nextPageCursor shouldBe "next"
        mapped.message shouldBe "Operation successful"
    }

    @Test
    fun `should map to different type`() {
        val original = CursorApiResponse(
            data = listOf(1, 2, 3),
            prevPageCursor = null,
            nextPageCursor = null,
        )

        val mapped: CursorApiResponse<String> = original.map { numbers ->
            numbers.map { "item-$it" }
        }

        mapped.data shouldBe listOf("item-1", "item-2", "item-3")
    }

    @Test
    fun `should preserve message after map`() {
        val original = CursorApiResponse(
            data = listOf("x"),
            prevPageCursor = null,
            nextPageCursor = null,
            message = "Custom message",
        )

        val mapped = original.map { it }

        mapped.message shouldBe "Custom message"
    }
}
