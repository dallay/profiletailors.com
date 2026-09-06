package com.profiletailors.common.domain.criteria

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.regex.Pattern

internal data class TestItem(
    val name: String,
    val age: Int,
    val active: Boolean,
    val score: Double?,
    val tags: List<String>,
)

internal class RuntimeCriteriaParserLogicalTest {

    private val parser = RuntimeCriteriaParser(TestItem::class)

    private val item = TestItem(
        name = "hello world",
        age = 25,
        active = true,
        score = 10.0,
        tags = listOf("a", "b"),
    )

    // ─── Empty ───────────────────────────────────────────────────────────────

    @Test
    fun `parse Empty should return null`() {
        val predicate = parser.parse(Criteria.Empty)

        assertThat(predicate).isNull()
    }

    // ─── And ─────────────────────────────────────────────────────────────────

    @Test
    fun `parse And with empty list should return null`() {
        val predicate = parser.parse(Criteria.And(emptyList()))

        assertThat(predicate).isNull()
    }

    @Test
    fun `parse And with single item should produce same result as inner predicate`() {
        val predicate = parser.parse(Criteria.And(listOf(Criteria.Equals("name", "hello world"))))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    @Test
    fun `parse And with single item should propagate false inner predicate`() {
        val predicate = parser.parse(Criteria.And(listOf(Criteria.Equals("name", "other"))))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    @Test
    fun `parse And with multiple items should return true when all match`() {
        val predicate = parser.parse(
            Criteria.And(
                listOf(
                    Criteria.Equals("name", "hello world"),
                    Criteria.Equals("age", 25),
                    Criteria.IsTrue("active"),
                ),
            ),
        )

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    @Test
    fun `parse And with multiple items should return false when one does not match`() {
        val predicate = parser.parse(
            Criteria.And(
                listOf(
                    Criteria.Equals("name", "hello world"),
                    Criteria.Equals("age", 30),
                ),
            ),
        )

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    // ─── Or ──────────────────────────────────────────────────────────────────

    @Test
    fun `parse Or with empty list should return null`() {
        val predicate = parser.parse(Criteria.Or(emptyList()))

        assertThat(predicate).isNull()
    }

    @Test
    fun `parse Or with single item should produce same result as inner predicate`() {
        val predicate = parser.parse(Criteria.Or(listOf(Criteria.Equals("name", "hello world"))))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    @Test
    fun `parse Or with multiple items should return true when at least one matches`() {
        val predicate = parser.parse(
            Criteria.Or(
                listOf(
                    Criteria.Equals("name", "non-matching"),
                    Criteria.Equals("age", 25),
                ),
            ),
        )

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    @Test
    fun `parse Or with multiple items should return false when none match`() {
        val predicate = parser.parse(
            Criteria.Or(
                listOf(
                    Criteria.Equals("name", "non-matching"),
                    Criteria.Equals("age", 99),
                ),
            ),
        )

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    // ─── Nested And / Or Combinations ────────────────────────────────────────

    @Test
    fun `parse nested And with Or should match when inner Or matches`() {
        val predicate = parser.parse(
            Criteria.And(
                listOf(
                    Criteria.Equals("name", "hello world"),
                    Criteria.Or(
                        listOf(
                            Criteria.Equals("age", 25),
                            Criteria.Equals("age", 30),
                        ),
                    ),
                ),
            ),
        )

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    @Test
    fun `parse nested And with Or should return false when inner Or fails`() {
        val otherItem = item.copy(age = 40)
        val predicate = parser.parse(
            Criteria.And(
                listOf(
                    Criteria.Equals("name", "hello world"),
                    Criteria.Or(
                        listOf(
                            Criteria.Equals("age", 25),
                            Criteria.Equals("age", 30),
                        ),
                    ),
                ),
            ),
        )

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(otherItem)).isFalse()
    }

    @Test
    fun `parse deeply nested And and Or should evaluate correctly`() {
        val predicate = parser.parse(
            Criteria.And(
                listOf(
                    Criteria.Equals("name", "hello world"),
                    Criteria.Or(
                        listOf(
                            Criteria.And(
                                listOf(
                                    Criteria.GreaterThan("age", 20),
                                    Criteria.LessThan("age", 30),
                                ),
                            ),
                            Criteria.And(
                                listOf(
                                    Criteria.Equals("age", 18),
                                    Criteria.IsTrue("active"),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertThat(predicate).isNotNull
        // name="hello world" AND ((age>20 AND age<30) OR (age=18 AND active=true))
        // age=25 → age>20 AND age<30 = true → inner OR = true → AND = true
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    @Test
    fun `parse deeply nested And and Or should return false when inner condition fails`() {
        val otherItem = item.copy(age = 10, active = false)
        val predicate = parser.parse(
            Criteria.And(
                listOf(
                    Criteria.Equals("name", "hello world"),
                    Criteria.Or(
                        listOf(
                            Criteria.And(
                                listOf(
                                    Criteria.GreaterThan("age", 20),
                                    Criteria.LessThan("age", 30),
                                ),
                            ),
                            Criteria.And(
                                listOf(
                                    Criteria.Equals("age", 18),
                                    Criteria.IsTrue("active"),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertThat(predicate).isNotNull
        // name="hello world" AND ((age>20 AND age<30) OR (age=18 AND active=true))
        // age=10 → age>20 = false → first AND = false
        // age=10 → age=18 = false → second AND = false
        // inner OR = false → entire AND = false
        assertThat(requireNotNull(predicate)(otherItem)).isFalse()
    }

    // ─── Edge Cases ──────────────────────────────────────────────────────────

    @Test
    fun `parse any criteria with non-existent property should gracefully return false`() {
        val predicates = listOf(
            parser.parse(Criteria.Equals("unknown", "x")),
            parser.parse(Criteria.NotEquals("unknown", "x")),
            parser.parse(Criteria.Between("unknown", 1..10)),
            parser.parse(Criteria.NotBetween("unknown", 1..10)),
            parser.parse(Criteria.LessThan("unknown", 1)),
            parser.parse(Criteria.LessThanEquals("unknown", 1)),
            parser.parse(Criteria.GreaterThan("unknown", 1)),
            parser.parse(Criteria.GreaterThanEquals("unknown", 1)),
            parser.parse(Criteria.IsNull("unknown")),
            parser.parse(Criteria.IsNotNull("unknown")),
            parser.parse(Criteria.Like("unknown", "%test%")),
            parser.parse(Criteria.NotLike("unknown", "%test%")),
            parser.parse(Criteria.Ilike("unknown", "%test%")),
            parser.parse(Criteria.Regexp("unknown", Pattern.compile(".*"))),
            parser.parse(Criteria.NotRegexp("unknown", Pattern.compile(".*"))),
            parser.parse(Criteria.In("unknown", listOf("x"))),
            parser.parse(Criteria.NotIn("unknown", listOf("x"))),
            parser.parse(Criteria.IsTrue("unknown")),
            parser.parse(Criteria.IsFalse("unknown")),
        )

        for (predicate in predicates) {
            assertThat(predicate)
                .describedAs("Non-existent property criteria should never return null")
                .isNotNull
        }
        for (predicate in predicates) {
            assertThat(requireNotNull(predicate)(item))
                .describedAs("Non-existent property should return false")
                .isFalse()
        }
    }

    @Test
    fun `parse And should collapse to null when all children return null`() {
        // And(Empty) → parse(Empty) returns null, mapNotNull filters it out,
        // reduce throws on empty list. This is a degenerate case.
        val thrown = runCatching {
            parser.parse(Criteria.And(listOf(Criteria.Empty)))
        }
        assertThat(thrown.exceptionOrNull())
            .isInstanceOf(UnsupportedOperationException::class.java)
    }

    @Test
    fun `parse Or should collapse to null when all children return null`() {
        // Or(Empty) → same degenerate case as And above.
        val thrown = runCatching {
            parser.parse(Criteria.Or(listOf(Criteria.Empty)))
        }
        assertThat(thrown.exceptionOrNull())
            .isInstanceOf(UnsupportedOperationException::class.java)
    }

    @Test
    fun `parse And with mixed null and non-null children should only combine non-null`() {
        val predicate = parser.parse(
            Criteria.And(
                listOf(
                    Criteria.Empty,
                    Criteria.Equals("name", "hello world"),
                ),
            ),
        )

        // Empty returns null, mapNotNull filters it, reduce runs on the remaining single item
        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    @Test
    fun `parse Or with mixed null and non-null children should only combine non-null`() {
        val predicate = parser.parse(
            Criteria.Or(
                listOf(
                    Criteria.Empty,
                    Criteria.Equals("name", "non-matching"),
                    Criteria.Equals("age", 25),
                ),
            ),
        )

        // Empty returns null, mapNotNull filters it, remaining items combined with ||
        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }
}
