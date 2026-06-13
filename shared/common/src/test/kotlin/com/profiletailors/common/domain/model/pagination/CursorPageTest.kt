package com.profiletailors.common.domain.model.pagination

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class CursorPageTest {

    @Test
    fun `should create a page with items`() {
        val page = CursorPage(
            data = listOf("a", "b", "c"),
            prevPageCursor = "cursor-prev",
            nextPageCursor = "cursor-next",
        )

        assertThat(page.data).containsExactly("a", "b", "c")
        assertThat(page.prevPageCursor).isEqualTo("cursor-prev")
        assertThat(page.nextPageCursor).isEqualTo("cursor-next")
    }

    @Test
    fun `should map items individually with mapItems`() {
        val page = CursorPage(
            data = listOf(1, 2, 3),
            prevPageCursor = "prev",
            nextPageCursor = "next",
        )

        val mapped = page.mapItems { it.toString() }

        assertThat(mapped.data).containsExactly("1", "2", "3")
    }

    @Test
    fun `should transform entire collection with map`() {
        val page = CursorPage(
            data = listOf(1, 2, 3, 4, 5),
            prevPageCursor = "prev",
            nextPageCursor = "next",
        )

        val mapped = page.map { it.filter { n -> n % 2 == 0 } }

        assertThat(mapped.data).containsExactly(2, 4)
    }

    @Test
    fun `should preserve cursors after map`() {
        val page = CursorPage(
            data = listOf("x", "y"),
            prevPageCursor = "cursor-prev",
            nextPageCursor = "cursor-next",
        )

        val mapped = page.map { it.map { e -> "$e-mapped" } }

        assertEquals(page.prevPageCursor, mapped.prevPageCursor)
        assertEquals(page.nextPageCursor, mapped.nextPageCursor)
    }

    @Test
    fun `should handle empty data`() {
        val page = CursorPage<Int>(
            data = emptyList(),
            prevPageCursor = "prev",
            nextPageCursor = "next",
        )

        assertThat(page.data).isEmpty()
        assertThat(page.mapItems { it }.data).isEmpty()
    }

    @Test
    fun `should handle null cursors`() {
        val page = CursorPage(
            data = listOf("a"),
        )

        assertThat(page.prevPageCursor).isNull()
        assertThat(page.nextPageCursor).isNull()
    }

    @Test
    fun `should handle data equality`() {
        val page1 = CursorPage(
            data = listOf(1, 2, 3),
            prevPageCursor = "prev",
            nextPageCursor = "next",
        )
        val page2 = CursorPage(
            data = listOf(1, 2, 3),
            prevPageCursor = "prev",
            nextPageCursor = "next",
        )

        assertEquals(page1, page2)
        assertEquals(page1.hashCode(), page2.hashCode())
    }

    @Test
    fun `should handle data inequality`() {
        val page1 = CursorPage(
            data = listOf(1, 2, 3),
            prevPageCursor = "prev",
            nextPageCursor = "next",
        )
        val page2 = CursorPage(
            data = listOf(4, 5, 6),
            prevPageCursor = "prev",
            nextPageCursor = "next",
        )

        assertNotEquals(page1, page2)
        assertNotEquals(page1.hashCode(), page2.hashCode())
    }
}
