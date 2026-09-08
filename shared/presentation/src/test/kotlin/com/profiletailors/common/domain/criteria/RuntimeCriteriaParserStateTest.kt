package com.profiletailors.common.domain.criteria

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class RuntimeCriteriaParserStateTest {

    private val parser = RuntimeCriteriaParser(TestItem::class)

    private val item = TestItem(
        name = "hello world",
        age = 25,
        active = true,
        score = 10.0,
        tags = listOf("a", "b"),
    )

    // ─── IsNull ──────────────────────────────────────────────────────────────

    @Test
    fun `parse IsNull should return true when property value is null`() {
        val nullItem = item.copy(score = null)
        val predicate = parser.parse(Criteria.IsNull("score"))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(nullItem)).isTrue()
    }

    @Test
    fun `parse IsNull should return false when property value is non-null`() {
        val predicate = parser.parse(Criteria.IsNull("name"))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    @Test
    fun `parse IsNull should return false for non-existent property`() {
        val predicate = parser.parse(Criteria.IsNull("nonexistent"))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    // ─── IsNotNull ───────────────────────────────────────────────────────────

    @Test
    fun `parse IsNotNull should return true when property value is non-null`() {
        val predicate = parser.parse(Criteria.IsNotNull("name"))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    @Test
    fun `parse IsNotNull should return false when property value is null`() {
        val nullItem = item.copy(score = null)
        val predicate = parser.parse(Criteria.IsNotNull("score"))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(nullItem)).isFalse()
    }

    @Test
    fun `parse IsNotNull should return false for non-existent property`() {
        val predicate = parser.parse(Criteria.IsNotNull("nonexistent"))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    // ─── In ──────────────────────────────────────────────────────────────────

    @Test
    fun `parse In should return true when value is contained in the list`() {
        val predicate = parser.parse(Criteria.In("name", listOf("hello world", "other")))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    @Test
    fun `parse In should return false when value is not in the list`() {
        val predicate = parser.parse(Criteria.In("name", listOf("other", "another")))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    @Test
    fun `parse In should return false for non-existent property`() {
        val predicate = parser.parse(Criteria.In("nonexistent", listOf("value")))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    @Test
    fun `parse In with null property value should return false`() {
        // The implementation uses `?.let` chaining, so when p.get(it) returns null
        // the let block is never entered and `null == true` evaluates to false.
        val nullItem = item.copy(score = null)
        val predicate = parser.parse(Criteria.In("score", listOf<Double?>(null, 20.0)))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(nullItem)).isFalse()
    }

    // ─── NotIn ───────────────────────────────────────────────────────────────

    @Test
    fun `parse NotIn should return true when value is not in the list`() {
        val predicate = parser.parse(Criteria.NotIn("name", listOf("other", "another")))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    @Test
    fun `parse NotIn should return false when value is in the list`() {
        val predicate = parser.parse(Criteria.NotIn("name", listOf("hello world", "other")))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    // ─── IsTrue ──────────────────────────────────────────────────────────────

    @Test
    fun `parse IsTrue should return true when property is true`() {
        val predicate = parser.parse(Criteria.IsTrue("active"))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    @Test
    fun `parse IsTrue should return false when property is false`() {
        val inactiveItem = item.copy(active = false)
        val predicate = parser.parse(Criteria.IsTrue("active"))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(inactiveItem)).isFalse()
    }

    @Test
    fun `parse IsTrue should return false for non-boolean property`() {
        val predicate = parser.parse(Criteria.IsTrue("name"))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    @Test
    fun `parse IsTrue should return false for non-existent property`() {
        val predicate = parser.parse(Criteria.IsTrue("nonexistent"))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    @Test
    fun `parse IsTrue should return false for null Boolean property`() {
        val nullItem = item.copy(score = null)
        val predicate = parser.parse(Criteria.IsTrue("score"))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(nullItem)).isFalse()
    }

    // ─── IsFalse ─────────────────────────────────────────────────────────────

    @Test
    fun `parse IsFalse should return true when property is false`() {
        val inactiveItem = item.copy(active = false)
        val predicate = parser.parse(Criteria.IsFalse("active"))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(inactiveItem)).isTrue()
    }

    @Test
    fun `parse IsFalse should return false when property is true`() {
        val predicate = parser.parse(Criteria.IsFalse("active"))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    @Test
    fun `parse IsFalse should return false for non-boolean property`() {
        val predicate = parser.parse(Criteria.IsFalse("name"))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    @Test
    fun `parse IsFalse should return false for non-existent property`() {
        val predicate = parser.parse(Criteria.IsFalse("nonexistent"))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    @Test
    fun `parse IsFalse should return false for null Boolean property`() {
        val nullItem = item.copy(score = null)
        val predicate = parser.parse(Criteria.IsFalse("score"))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(nullItem)).isFalse()
    }
}
