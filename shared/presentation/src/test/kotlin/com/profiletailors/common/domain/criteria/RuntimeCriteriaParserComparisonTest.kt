package com.profiletailors.common.domain.criteria

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class RuntimeCriteriaParserComparisonTest {

    private val parser = RuntimeCriteriaParser(TestItem::class)

    private val item = TestItem(
        name = "hello world",
        age = 25,
        active = true,
        score = 10.0,
        tags = listOf("a", "b"),
    )

    // ─── Equals ──────────────────────────────────────────────────────────────

    @Test
    fun `parse Equals should return true when property value matches`() {
        val predicate = parser.parse(Criteria.Equals("name", "hello world"))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    @Test
    fun `parse Equals should return false when property value differs`() {
        val predicate = parser.parse(Criteria.Equals("name", "other"))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    @Test
    fun `parse Equals should return false for non-existent property`() {
        val predicate = parser.parse(Criteria.Equals("nonexistent", "value"))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    @Test
    fun `parse Equals should match integer properties`() {
        val predicate = parser.parse(Criteria.Equals("age", 25))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    // ─── NotEquals ───────────────────────────────────────────────────────────

    @Test
    fun `parse NotEquals should return true when property value differs`() {
        val predicate = parser.parse(Criteria.NotEquals("name", "other"))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    @Test
    fun `parse NotEquals should return false when property value matches`() {
        val predicate = parser.parse(Criteria.NotEquals("name", "hello world"))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    // ─── Between ─────────────────────────────────────────────────────────────

    @Test
    fun `parse Between should return true when value is inside the range`() {
        val predicate = parser.parse(Criteria.Between("age", 20..30))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    @Test
    fun `parse Between should return true at the lower bound`() {
        val predicate = parser.parse(Criteria.Between("age", 25..35))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    @Test
    fun `parse Between should return true at the upper bound`() {
        val predicate = parser.parse(Criteria.Between("age", 15..25))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    @Test
    fun `parse Between should return false when value is below the range`() {
        val predicate = parser.parse(Criteria.Between("age", 30..40))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    @Test
    fun `parse Between should return false when value is above the range`() {
        val predicate = parser.parse(Criteria.Between("age", 10..20))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    @Test
    fun `parse Between with null property value should return false`() {
        val nullItem = item.copy(score = null)
        val predicate = parser.parse(Criteria.Between("score", 5.0..15.0))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(nullItem)).isFalse()
    }

    @Test
    fun `parse Between with non-Comparable property type should return false`() {
        val predicate = parser.parse(Criteria.Between("tags", 1..10))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    @Test
    fun `parse Between with non-existent property should return false`() {
        val predicate = parser.parse(Criteria.Between("nonexistent", 1..10))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    // ─── NotBetween ──────────────────────────────────────────────────────────

    @Test
    fun `parse NotBetween should return true when value is below the range`() {
        val predicate = parser.parse(Criteria.NotBetween("age", 30..40))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    @Test
    fun `parse NotBetween should return true when value is above the range`() {
        val predicate = parser.parse(Criteria.NotBetween("age", 10..20))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    @Test
    fun `parse NotBetween should return false when value is inside the range`() {
        val predicate = parser.parse(Criteria.NotBetween("age", 20..30))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    @Test
    fun `parse NotBetween should return false at the lower bound`() {
        val predicate = parser.parse(Criteria.NotBetween("age", 25..35))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    @Test
    fun `parse NotBetween should return false at the upper bound`() {
        val predicate = parser.parse(Criteria.NotBetween("age", 15..25))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    @Test
    fun `parse NotBetween with null property value should return true`() {
        // The implementation uses `!= false` for the inner let block, so
        // when v is null the safe-call returns null and `null != false` is true.
        val nullItem = item.copy(score = null)
        val predicate = parser.parse(Criteria.NotBetween("score", 5.0..15.0))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(nullItem)).isTrue()
    }

    @Test
    fun `parse NotBetween with non-Comparable property type should return false`() {
        val predicate = parser.parse(Criteria.NotBetween("tags", 1..10))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    // ─── LessThan ────────────────────────────────────────────────────────────

    @Test
    fun `parse LessThan should return true when value is strictly less`() {
        val predicate = parser.parse(Criteria.LessThan("age", 30))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    @Test
    fun `parse LessThan should return false when value equals`() {
        val predicate = parser.parse(Criteria.LessThan("age", 25))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    @Test
    fun `parse LessThan should return false when value is greater`() {
        val predicate = parser.parse(Criteria.LessThan("age", 20))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    @Test
    fun `parse LessThan with null property value should return false`() {
        val nullItem = item.copy(score = null)
        val predicate = parser.parse(Criteria.LessThan("score", 20.0))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(nullItem)).isFalse()
    }

    // ─── LessThanEquals ──────────────────────────────────────────────────────

    @Test
    fun `parse LessThanEquals should return true when value is less`() {
        val predicate = parser.parse(Criteria.LessThanEquals("age", 30))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    @Test
    fun `parse LessThanEquals should return true when value equals`() {
        val predicate = parser.parse(Criteria.LessThanEquals("age", 25))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    @Test
    fun `parse LessThanEquals should return false when value is greater`() {
        val predicate = parser.parse(Criteria.LessThanEquals("age", 20))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    @Test
    fun `parse LessThanEquals with null property value should return false`() {
        val nullItem = item.copy(score = null)
        val predicate = parser.parse(Criteria.LessThanEquals("score", 20.0))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(nullItem)).isFalse()
    }

    // ─── GreaterThan ─────────────────────────────────────────────────────────

    @Test
    fun `parse GreaterThan should return true when value is strictly greater`() {
        val predicate = parser.parse(Criteria.GreaterThan("age", 20))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    @Test
    fun `parse GreaterThan should return false when value equals`() {
        val predicate = parser.parse(Criteria.GreaterThan("age", 25))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    @Test
    fun `parse GreaterThan should return false when value is less`() {
        val predicate = parser.parse(Criteria.GreaterThan("age", 30))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    @Test
    fun `parse GreaterThan with null property value should return false`() {
        val nullItem = item.copy(score = null)
        val predicate = parser.parse(Criteria.GreaterThan("score", 5.0))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(nullItem)).isFalse()
    }

    // ─── GreaterThanEquals ───────────────────────────────────────────────────

    @Test
    fun `parse GreaterThanEquals should return true when value is greater`() {
        val predicate = parser.parse(Criteria.GreaterThanEquals("age", 20))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    @Test
    fun `parse GreaterThanEquals should return true when value equals`() {
        val predicate = parser.parse(Criteria.GreaterThanEquals("age", 25))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    @Test
    fun `parse GreaterThanEquals should return false when value is less`() {
        val predicate = parser.parse(Criteria.GreaterThanEquals("age", 30))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    @Test
    fun `parse GreaterThanEquals with null property value should return false`() {
        val nullItem = item.copy(score = null)
        val predicate = parser.parse(Criteria.GreaterThanEquals("score", 5.0))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(nullItem)).isFalse()
    }
}
