package com.profiletailors.common.domain.criteria

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class CriteriaStepTest {

    private val step = CriteriaStep("status")

    @Test
    fun `is should create equals criteria`() {
        assertThat(step.`is`("published")).isEqualTo(Criteria.Equals("status", "published"))
    }

    @Test
    fun `not should create not equals criteria`() {
        assertThat(step.not("deleted")).isEqualTo(Criteria.NotEquals("status", "deleted"))
    }

    @Test
    fun `lessThan should create less than criteria`() {
        assertThat(step.lessThan(5)).isEqualTo(Criteria.LessThan("status", 5))
    }

    @Test
    fun `greaterThan should create greater than criteria`() {
        assertThat(step.greaterThan(5)).isEqualTo(Criteria.GreaterThan("status", 5))
    }

    @Test
    fun `isNull should create is null criteria`() {
        assertThat(step.isNull()).isEqualTo(Criteria.IsNull("status"))
    }

    @Test
    fun `isNotNull should create is not null criteria`() {
        assertThat(step.isNotNull()).isEqualTo(Criteria.IsNotNull("status"))
    }

    @Test
    fun `like should create like criteria`() {
        assertThat(step.like("%test%")).isEqualTo(Criteria.Like("status", "%test%"))
    }

    @Test
    fun `in should create in criteria from vararg`() {
        assertThat(step.`in`("a", "b", "c")).isEqualTo(Criteria.In("status", listOf("a", "b", "c")))
    }

    @Test
    fun `notIn should create not in criteria from vararg`() {
        assertThat(step.notIn("a", "b")).isEqualTo(Criteria.NotIn("status", listOf("a", "b")))
    }

    @Test
    fun `between should create between criteria`() {
        val range = 1..10
        assertThat(step.between(range)).isEqualTo(Criteria.Between("status", range))
    }

    @Test
    fun `isTrue should create is true criteria`() {
        assertThat(step.isTrue()).isEqualTo(Criteria.IsTrue("status"))
    }

    @Test
    fun `isFalse should create is false criteria`() {
        assertThat(step.isFalse()).isEqualTo(Criteria.IsFalse("status"))
    }

    @Test
    fun `where should create criteria step`() {
        val step = where("name")

        assertThat(step.`is`("test")).isEqualTo(Criteria.Equals("name", "test"))
    }

    @Test
    fun `and extension should combine criteria`() {
        val result = Criteria.Equals("a", 1).and(Criteria.Equals("b", 2))

        assertThat(result).isEqualTo(Criteria.And(listOf(Criteria.Equals("a", 1), Criteria.Equals("b", 2))))
    }

    @Test
    fun `or extension should combine criteria`() {
        val result = Criteria.Equals("a", 1).or(Criteria.Equals("b", 2))

        assertThat(result).isEqualTo(Criteria.Or(listOf(Criteria.Equals("a", 1), Criteria.Equals("b", 2))))
    }
}
