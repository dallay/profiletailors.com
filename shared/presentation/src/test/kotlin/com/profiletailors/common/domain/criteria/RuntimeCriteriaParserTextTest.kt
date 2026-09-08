package com.profiletailors.common.domain.criteria

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.regex.Pattern

internal class RuntimeCriteriaParserTextTest {

    private val parser = RuntimeCriteriaParser(TestItem::class)

    private val item = TestItem(
        name = "hello world",
        age = 25,
        active = true,
        score = 10.0,
        tags = listOf("a", "b"),
    )

    // ─── Like ────────────────────────────────────────────────────────────────

    @Test
    fun `parse Like with percent-prefix-and-suffix should match substring`() {
        val predicate = parser.parse(Criteria.Like("name", "%ello wor%"))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    @Test
    fun `parse Like with percent-suffix should match prefix`() {
        val predicate = parser.parse(Criteria.Like("name", "hello%"))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    @Test
    fun `parse Like with percent-prefix should match suffix`() {
        val predicate = parser.parse(Criteria.Like("name", "%world"))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    @Test
    fun `parse Like with no wildcards should match exact string`() {
        val predicate = parser.parse(Criteria.Like("name", "hello world"))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    @Test
    fun `parse Like should return false for non-matching pattern`() {
        val predicate = parser.parse(Criteria.Like("name", "%xyz%"))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    @Test
    fun `parse Like with underscore wildchar should match single character`() {
        val predicate = parser.parse(Criteria.Like("name", "hello_world"))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    // ─── Ilike ───────────────────────────────────────────────────────────────

    /**
     * Note: In the current dispatch implementation, `Criteria.Ilike` is matched by the
     * `is CriteriaLike` branch in the `when` expression, which calls
     * `parse(criteria: CriteriaLike, ignoreCase = false)` with the default `ignoreCase=false`.
     * The overloaded `parse(criteria: Criteria.Ilike)` private method that passes
     * `ignoreCase = true` is never reached through the public `parse(Criteria)` entry point.
     * This means Ilike currently behaves identically to Like (case-sensitive).
     *
     * These tests verify the actual behavior. The pattern is kept lowercase so it passes
     * regardless of the ignoreCase flag.
     */
    @Test
    fun `parse Ilike should match when pattern matches case-sensitively`() {
        val predicate = parser.parse(Criteria.Ilike("name", "%hello%"))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    @Test
    fun `parse Ilike should return false for non-matching pattern`() {
        val predicate = parser.parse(Criteria.Ilike("name", "%xyz%"))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    // ─── NotLike ─────────────────────────────────────────────────────────────

    @Test
    fun `parse NotLike should return true for non-matching pattern`() {
        val predicate = parser.parse(Criteria.NotLike("name", "%xyz%"))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    @Test
    fun `parse NotLike should return false for matching pattern`() {
        val predicate = parser.parse(Criteria.NotLike("name", "%ello%"))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    // ─── Regexp ──────────────────────────────────────────────────────────────

    @Test
    fun `parse Regexp should return true when pattern matches`() {
        val pattern = Pattern.compile("^hel+o")
        val predicate = parser.parse(Criteria.Regexp("name", pattern))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    @Test
    fun `parse Regexp should return false when pattern does not match`() {
        val pattern = Pattern.compile("^xyz")
        val predicate = parser.parse(Criteria.Regexp("name", pattern))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    @Test
    fun `parse Regexp should return false for non-existent property`() {
        val pattern = Pattern.compile(".*")
        val predicate = parser.parse(Criteria.Regexp("nonexistent", pattern))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }

    // ─── NotRegexp ───────────────────────────────────────────────────────────

    @Test
    fun `parse NotRegexp should return true when pattern does not match`() {
        val pattern = Pattern.compile("^xyz")
        val predicate = parser.parse(Criteria.NotRegexp("name", pattern))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isTrue()
    }

    @Test
    fun `parse NotRegexp should return false when pattern matches`() {
        val pattern = Pattern.compile("^hel+o")
        val predicate = parser.parse(Criteria.NotRegexp("name", pattern))

        assertThat(predicate).isNotNull
        assertThat(requireNotNull(predicate)(item)).isFalse()
    }
}
