package com.profiletailors.common.domain.presentation.pagination

import com.profiletailors.common.domain.bus.query.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class CursorPageResponseTest {

    @Test
    fun `should create response with items`() {
        val response = CursorPageResponse(
            data = listOf("a", "b", "c"),
            prevPageCursor = "cursor-prev",
            nextPageCursor = "cursor-next",
        )

        assertThat(response.data).containsExactly("a", "b", "c")
        assertThat(response.prevPageCursor).isEqualTo("cursor-prev")
        assertThat(response.nextPageCursor).isEqualTo("cursor-next")
    }

    @Test
    fun `should allow null cursors for boundaries`() {
        val firstPage = CursorPageResponse(data = listOf("a"), prevPageCursor = null, nextPageCursor = "next")
        val lastPage = CursorPageResponse(data = listOf("z"), prevPageCursor = "prev", nextPageCursor = null)

        assertThat(firstPage.prevPageCursor).isNull()
        assertThat(lastPage.nextPageCursor).isNull()
    }

    @Test
    fun `should map items`() {
        val response = CursorPageResponse(
            data = listOf(1, 2, 3),
            prevPageCursor = "prev",
            nextPageCursor = "next",
        )

        val mapped = response.map { it.map { n -> n.toString() } }

        assertThat(mapped.data).containsExactly("1", "2", "3")
        assertThat(mapped.prevPageCursor).isEqualTo("prev")
        assertThat(mapped.nextPageCursor).isEqualTo("next")
    }

    @Test
    fun `should implement Response interface`() {
        val response = CursorPageResponse(data = listOf("a"), prevPageCursor = null, nextPageCursor = null)

        assertThat(response).isInstanceOf(Response::class.java)
    }

    @Test
    fun `should support data class equality`() {
        val r1 = CursorPageResponse(listOf("a"), "p", "n")
        val r2 = CursorPageResponse(listOf("a"), "p", "n")

        assertThat(r1).isEqualTo(r2)
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode())
    }
}
