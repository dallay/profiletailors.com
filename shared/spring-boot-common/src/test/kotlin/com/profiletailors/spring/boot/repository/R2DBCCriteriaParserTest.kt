package com.profiletailors.spring.boot.repository

import com.profiletailors.common.domain.criteria.Criteria
import java.time.LocalDate
import java.util.regex.Pattern
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.springframework.data.relational.core.mapping.Column

class R2DBCCriteriaParserTest {

    private data class TestEntity(
        val name: String,
        val age: Int,
        val active: Boolean,
        @field:Column("created_at")
        val createdAt: LocalDate,
    )

    private val parser = R2DBCCriteriaParser(TestEntity::class)

    @Test
    fun `should resolve annotated column names`() {
        val criteria = parser.parse(Criteria.Equals("createdAt", LocalDate.parse("2024-01-01")))

        assertContains(criteria.toString(), "created_at")
    }

    @Test
    fun `should parse equality comparisons`() {
        val criteria = parser.parse(Criteria.Equals("name", "Ada"))

        assertContains(criteria.toString(), "name")
        assertContains(criteria.toString(), "Ada")
    }

    @Test
    fun `should parse numeric comparison criteria`() {
        val lessThan = parser.parse(Criteria.LessThan("age", 18)).toString()
        val greaterThanEquals = parser.parse(Criteria.GreaterThanEquals("age", 21)).toString()

        assertContains(lessThan, "age")
        assertContains(lessThan, "18")
        assertContains(greaterThanEquals, "age")
        assertContains(greaterThanEquals, "21")
    }

    @Test
    fun `should parse null and boolean criteria`() {
        val isNull = parser.parse(Criteria.IsNull("name")).toString()
        val isTrue = parser.parse(Criteria.IsTrue("active")).toString()
        val isFalse = parser.parse(Criteria.IsFalse("active")).toString()

        assertContains(isNull, "name")
        assertContains(isTrue, "active")
        assertContains(isFalse, "active")
    }

    @Test
    fun `should parse like and not like criteria`() {
        val like = parser.parse(Criteria.Like("name", "%Ada%")).toString()
        val ilike = parser.parse(Criteria.Ilike("name", "%ada%")).toString()
        val notLike = parser.parse(Criteria.NotLike("name", "%bot%")).toString()

        assertContains(like, "name")
        assertContains(ilike, "name")
        assertContains(notLike, "name")
    }

    @Test
    fun `should parse collection criteria`() {
        val inCriteria = parser.parse(Criteria.In("name", listOf("Ada", "Grace"))).toString()
        val notInCriteria = parser.parse(Criteria.NotIn("name", listOf("bot"))).toString()

        assertContains(inCriteria, "name")
        assertContains(notInCriteria, "name")
    }

    @Test
    fun `should parse between criteria`() {
        val between = parser.parse(Criteria.Between("createdAt", LocalDate.parse("2024-01-01")..LocalDate.parse("2024-12-31"))).toString()
        val notBetween = parser.parse(Criteria.NotBetween("createdAt", LocalDate.parse("2023-01-01")..LocalDate.parse("2023-12-31"))).toString()

        assertContains(between, "created_at")
        assertContains(notBetween, "created_at")
    }

    @Test
    fun `should parse nested and or criteria`() {
        val criteria = parser.parse(
            Criteria.And(
                listOf(
                    Criteria.Equals("name", "Ada"),
                    Criteria.Or(
                        listOf(
                            Criteria.GreaterThan("age", 18),
                            Criteria.IsTrue("active"),
                        ),
                    ),
                ),
            ),
        ).toString()

        assertContains(criteria, "name")
        assertContains(criteria, "age")
        assertContains(criteria, "active")
    }

    @Test
    fun `should reject unknown properties`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            parser.parse(Criteria.Equals("missing", "value"))
        }

        assertContains(exception.message.orEmpty(), "missing is invalid")
    }

    @Test
    fun `should reject unsupported criteria`() {
        assertFailsWith<IllegalArgumentException> {
            parser.parse(Criteria.Regexp("name", Pattern.compile(".*")))
        }
    }

    private fun assertContains(actual: String, expected: String) {
        assertTrue(
            actual.contains(expected),
            "Expected <$actual> to contain <$expected>",
        )
    }
}
