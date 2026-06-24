package com.profiletailors.common.domain.presentation.pagination

import com.profiletailors.common.domain.bus.query.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class OffsetPageResponseTest {

    @Test
    fun `should create response with items and pagination`() {
        val response = OffsetPageResponse(
            data = listOf("a", "b"),
            total = 10L,
            perPage = 2,
            page = 1,
            totalPages = 5,
        )

        assertThat(response.data).containsExactly("a", "b")
        assertThat(response.total).isEqualTo(10L)
        assertThat(response.perPage).isEqualTo(2)
        assertThat(response.page).isEqualTo(1)
        assertThat(response.totalPages).isEqualTo(5)
    }

    @Test
    fun `should allow null total`() {
        val response = OffsetPageResponse(data = listOf("a"), perPage = 10)

        assertThat(response.total).isNull()
    }

    @Test
    fun `should map items`() {
        val response = OffsetPageResponse(
            data = listOf(1, 2),
            total = 5L,
            perPage = 2,
            page = 1,
            totalPages = 3,
        )

        val mapped = response.map { it.map { n -> n.toString() } }

        assertThat(mapped.data).containsExactly("1", "2")
        assertThat(mapped.total).isEqualTo(5L)
        assertThat(mapped.perPage).isEqualTo(2)
        assertThat(mapped.page).isEqualTo(1)
        assertThat(mapped.totalPages).isEqualTo(3)
    }

    @Test
    fun `should implement Response interface`() {
        val response = OffsetPageResponse(data = listOf("a"), perPage = 10)

        assertThat(response).isInstanceOf(Response::class.java)
    }

    @Test
    fun `should support data class equality`() {
        val r1 = OffsetPageResponse(listOf("a"), 10L, 5, 1, 2)
        val r2 = OffsetPageResponse(listOf("a"), 10L, 5, 1, 2)

        assertThat(r1).isEqualTo(r2)
    }
}
