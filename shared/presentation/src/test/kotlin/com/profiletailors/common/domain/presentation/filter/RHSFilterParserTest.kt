package com.profiletailors.common.domain.presentation.filter

import com.profiletailors.common.domain.criteria.Criteria
import com.profiletailors.common.domain.presentation.FilterInvalidException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.ObjectMapper
import kotlin.reflect.KProperty1

internal class RHSFilterParserTest {

    data class TestResource(
        val name: String = "",
        val age: Int = 0,
        val status: String = "",
    )

    private val parser = RHSFilterParser(TestResource::class, ObjectMapper())

    @Test
    fun `should parse equals operator`() {
        val result = parser.parse(queryOf(TestResource::name to listOf("eq:john")))

        assertThat(result).isEqualTo(Criteria.And(listOf(Criteria.Equals("name", "john"))))
    }

    @Test
    fun `should parse not equals operator`() {
        val result = parser.parse(queryOf(TestResource::status to listOf("ne:deleted")))

        assertThat(result).isEqualTo(Criteria.And(listOf(Criteria.NotEquals("status", "deleted"))))
    }

    @Test
    fun `should parse less than operator`() {
        val result = parser.parse(queryOf(TestResource::age to listOf("lt:25")))

        assertThat(result).isEqualTo(Criteria.And(listOf(Criteria.LessThan("age", 25))))
    }

    @Test
    fun `should parse greater than or equals operator`() {
        val result = parser.parse(queryOf(TestResource::age to listOf("gte:18")))

        assertThat(result).isEqualTo(Criteria.And(listOf(Criteria.GreaterThanEquals("age", 18))))
    }

    @Test
    fun `should parse greater than operator`() {
        val result = parser.parse(queryOf(TestResource::age to listOf("gt:18")))

        assertThat(result).isEqualTo(Criteria.And(listOf(Criteria.GreaterThan("age", 18))))
    }

    @Test
    fun `should parse like operator`() {
        val result = parser.parse(queryOf(TestResource::name to listOf("lk:%test%")))

        assertThat(result).isEqualTo(Criteria.And(listOf(Criteria.Like("name", "%test%"))))
    }

    @Test
    fun `should combine multiple fields with AND by default`() {
        val result = parser.parse(queryOf(
            TestResource::status to listOf("eq:active"),
            TestResource::age to listOf("gte:18"),
        ))

        assertThat(result).isInstanceOf(Criteria.And::class.java)
        val and = result as Criteria.And
        assertThat(and.value).hasSize(2)
    }

    @Test
    fun `should combine with OR when specified`() {
        val result = parser.parse(
            queryOf(TestResource::status to listOf("eq:active")),
            useOr = true,
        )

        assertThat(result).isInstanceOf(Criteria.Or::class.java)
    }

    @Test
    fun `should return Empty for empty query`() {
        val result = parser.parse(emptyMap())

        assertThat(result).isEqualTo(Criteria.Empty)
    }

    @Test
    fun `should throw on unsupported operator`() {
        assertThrows<FilterInvalidException> {
            parser.parse(queryOf(TestResource::name to listOf("xx:value")))
        }
    }

    companion object {
        private fun <T> queryOf(
            vararg entries: Pair<KProperty1<T, *>, Collection<String?>?>,
        ): Map<KProperty1<T, *>, Collection<String?>?> = mapOf(*entries)
    }
}
