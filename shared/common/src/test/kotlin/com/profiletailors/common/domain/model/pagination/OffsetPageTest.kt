package com.profiletailors.common.domain.model.pagination

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class OffsetPageTest {

    @Test
    fun `should create a page with items`() {
        val page = OffsetPage(
            data = listOf("a", "b", "c"),
            total = 10L,
            perPage = 3,
            page = 1,
            totalPages = 4,
        )

        assertThat(page.data).containsExactly("a", "b", "c")
        assertThat(page.total).isEqualTo(10L)
        assertThat(page.perPage).isEqualTo(3)
        assertThat(page.page).isEqualTo(1)
        assertThat(page.totalPages).isEqualTo(4)
    }

    @Test
    fun `should map items individually with mapItems`() {
        val page = OffsetPage(
            data = listOf(1, 2, 3),
            total = 100L,
            perPage = 10,
            page = 1,
            totalPages = 10,
        )

        val mapped = page.mapItems { it.toString() }

        assertThat(mapped.data).containsExactly("1", "2", "3")
    }

    @Test
    fun `should transform entire collection with map`() {
        val page = OffsetPage(
            data = listOf(1, 2, 3, 4, 5),
            total = 5L,
            perPage = 5,
            page = 1,
            totalPages = 1,
        )

        val mapped = page.map { it.filter { n -> n % 2 == 0 } }

        assertThat(mapped.data).containsExactly(2, 4)
    }

    @Test
    fun `should preserve page metadata after map`() {
        val page = OffsetPage(
            data = listOf("x", "y"),
            total = 50L,
            perPage = 2,
            page = 5,
            totalPages = 25,
        )

        val mapped = page.map { it.map { e -> "$e-mapped" } }

        assertEquals(page.total, mapped.total)
        assertEquals(page.perPage, mapped.perPage)
        assertEquals(page.page, mapped.page)
        assertEquals(page.totalPages, mapped.totalPages)
    }

    @Test
    fun `should handle empty data`() {
        val page = OffsetPage<Int>(
            data = emptyList(),
            total = 0L,
            perPage = 10,
            page = 1,
            totalPages = 0,
        )

        assertThat(page.data).isEmpty()
        assertThat(page.mapItems { it }.data).isEmpty()
    }

    @Test
    fun `should handle null total`() {
        val page = OffsetPage(
            data = listOf("a"),
            perPage = 10,
        )

        assertThat(page.total).isNull()
        assertThat(page.totalPages).isNull()
    }

    @Test
    fun `should maintain data equality`() {
        val page1 = OffsetPage(
            data = listOf(1, 2, 3),
            total = 30L,
            perPage = 3,
            page = 1,
            totalPages = 10,
        )
        val page2 = OffsetPage(
            data = listOf(1, 2, 3),
            total = 30L,
            perPage = 3,
            page = 1,
            totalPages = 10,
        )

        assertEquals(page1, page2)
        assertEquals(page1.hashCode(), page2.hashCode())
    }

    @Test
    fun `should consider different data as not equal`() {
        val page1 = OffsetPage(
            data = listOf(1, 2, 3),
            total = 30L,
            perPage = 3,
            page = 1,
            totalPages = 10,
        )
        val page2 = OffsetPage(
            data = listOf(4, 5, 6),
            total = 30L,
            perPage = 3,
            page = 1,
            totalPages = 10,
        )

        assertNotEquals(page1, page2)
        assertNotEquals(page1.hashCode(), page2.hashCode())
    }
}
