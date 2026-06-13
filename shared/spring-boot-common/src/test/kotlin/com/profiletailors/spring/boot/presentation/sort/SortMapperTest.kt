package com.profiletailors.spring.boot.presentation.sort

import com.profiletailors.common.domain.presentation.sort.Direction
import com.profiletailors.common.domain.presentation.sort.Sort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SortMapperTest {

    @Test
    fun `should map null sort to unsorted Spring sort`() {
        val springSort = null.toSpringSort()

        assertFalse(springSort.isSorted)
    }

    @Test
    fun `should map ascending and descending orders`() {
        val sort = Sort.by(Direction.ASC, "name")
            .and(Sort.by(Direction.DESC, "createdAt"))

        val springSort = sort.toSpringSort()

        val orders = springSort.toList()
        assertEquals(2, orders.size)
        assertEquals("name", orders[0].property)
        assertEquals(org.springframework.data.domain.Sort.Direction.ASC, orders[0].direction)
        assertEquals("createdAt", orders[1].property)
        assertEquals(org.springframework.data.domain.Sort.Direction.DESC, orders[1].direction)
    }
}
