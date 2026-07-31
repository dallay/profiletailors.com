package com.profiletailors.smp.platformadmin.application.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PagedResultTest {

    @Test
    fun `should cap totalPages at Int MAX_VALUE when totalElements is Long MAX_VALUE`() {
        val result = PagedResult.of(items = emptyList<String>(), page = 0, size = 25, totalElements = Long.MAX_VALUE)

        assertEquals(Int.MAX_VALUE, result.totalPages)
        assertEquals(true, result.hasNext)
        assertEquals(false, result.hasPrevious)
    }

    @Test
    fun `should compute ceiling for totalPages when page is partial`() {
        val result = PagedResult.of(items = emptyList<String>(), page = 0, size = 10, totalElements = 25)

        assertEquals(3, result.totalPages)
        assertEquals(true, result.hasNext)
    }

    @Test
    fun `should return zero totalPages when size is zero`() {
        val result = PagedResult.of(items = emptyList<String>(), page = 0, size = 0, totalElements = 100)

        assertEquals(0, result.totalPages)
        assertEquals(false, result.hasNext)
    }

    @Test
    fun `should return zero totalPages when no elements exist`() {
        val result = PagedResult.of(items = emptyList<String>(), page = 0, size = 25, totalElements = 0)

        assertEquals(0, result.totalPages)
        assertEquals(false, result.hasNext)
    }
}
