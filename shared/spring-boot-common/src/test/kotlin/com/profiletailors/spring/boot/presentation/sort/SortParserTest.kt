package com.profiletailors.spring.boot.presentation.sort

import com.profiletailors.common.domain.presentation.SortInvalidException
import com.profiletailors.common.domain.presentation.sort.Direction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.springframework.data.relational.core.mapping.Column
import tools.jackson.module.kotlin.jsonMapper

class SortParserTest {

    private data class TestEntity(
        val name: String,
        @field:Column("created_at")
        val createdAt: String,
    )

    private val parser = SortParser(TestEntity::class, jsonMapper())

    @Test
    fun `should parse ascending sort using exported property name`() {
        val sort = parser.parse("asc:name")

        assertEquals(1, sort.orders.size)
        assertEquals(Direction.ASC, sort.orders[0].direction)
        assertEquals("name", sort.orders[0].property)
    }

    @Test
    fun `should parse descending sort and map column annotation`() {
        val sort = parser.parse("desc:createdAt")

        assertEquals(1, sort.orders.size)
        assertEquals(Direction.DESC, sort.orders[0].direction)
        assertEquals("created_at", sort.orders[0].property)
    }

    @Test
    fun `should combine multiple sort expressions`() {
        val sort = parser.parse(listOf("asc:name", "desc:createdAt"))

        assertEquals(2, sort.orders.size)
        assertEquals("name", sort.orders[0].property)
        assertEquals("created_at", sort.orders[1].property)
    }

    @Test
    fun `should reject malformed sort expression`() {
        assertFailsWith<SortInvalidException> {
            parser.parse("name")
        }
    }

    @Test
    fun `should reject unknown property`() {
        assertFailsWith<SortInvalidException> {
            parser.parse("asc:missing")
        }
    }

    @Test
    fun `should reject unknown direction`() {
        assertFailsWith<SortInvalidException> {
            parser.parse("sideways:name")
        }
    }
}
