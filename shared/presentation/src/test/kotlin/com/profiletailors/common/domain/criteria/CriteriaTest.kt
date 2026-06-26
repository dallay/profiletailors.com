package com.profiletailors.common.domain.criteria

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.regex.Pattern

internal class CriteriaTest {

    @Test
    fun `empty criteria should match everything`() {
        val empty = Criteria.Empty

        assertThat(empty.toString()).isEqualTo("()")
    }

    @Test
    fun `and criteria should combine with AND`() {
        val criteria = Criteria.And(
            listOf(
                Criteria.Equals("status", "published"),
                Criteria.Equals("featured", true),
            ),
        )

        assertThat(criteria.toString()).isEqualTo("(status = published AND featured = true)")
    }

    @Test
    fun `or criteria should combine with OR`() {
        val criteria = Criteria.Or(
            listOf(
                Criteria.Equals("status", "draft"),
                Criteria.Equals("status", "published"),
            ),
        )

        assertThat(criteria.toString()).isEqualTo("(status = draft OR status = published)")
    }

    @Test
    fun `equals criteria should produce correct string`() {
        assertThat(Criteria.Equals("name", "test").toString()).isEqualTo("name = test")
    }

    @Test
    fun `not equals criteria should produce correct string`() {
        assertThat(Criteria.NotEquals("name", "test").toString()).isEqualTo("name != test")
    }

    @Test
    fun `between criteria should produce correct string`() {
        val range = 1..10
        assertThat(Criteria.Between("priority", range).toString()).isEqualTo("(priority >= 1 && priority <= 10)")
    }

    @Test
    fun `less than criteria should produce correct string`() {
        assertThat(Criteria.LessThan("priority", 5).toString()).isEqualTo("priority < 5")
    }

    @Test
    fun `greater than criteria should produce correct string`() {
        assertThat(Criteria.GreaterThan("priority", 5).toString()).isEqualTo("priority > 5")
    }

    @Test
    fun `is null criteria should produce correct string`() {
        assertThat(Criteria.IsNull("deletedAt").toString()).isEqualTo("deletedAt = null")
    }

    @Test
    fun `is not null criteria should produce correct string`() {
        assertThat(Criteria.IsNotNull("deletedAt").toString()).isEqualTo("deletedAt != null")
    }

    @Test
    fun `like criteria should produce correct string`() {
        assertThat(Criteria.Like("name", "%test%").toString()).isEqualTo("name LIKE %test%")
    }

    @Test
    fun `in criteria should produce correct string`() {
        val criteria = Criteria.In("status", listOf("draft", "published"))

        assertThat(criteria.toString()).isEqualTo("status IN [draft, published]")
    }

    @Test
    fun `not in criteria should produce correct string`() {
        val criteria = Criteria.NotIn("status", listOf("deleted"))

        assertThat(criteria.toString()).isEqualTo("status NOT IN [deleted]")
    }

    @Test
    fun `regexp criteria should produce correct string`() {
        val pattern = Pattern.compile("^test")
        val criteria = Criteria.Regexp("name", pattern)

        assertThat(criteria.toString()).isEqualTo("name REGEXP ^test")
    }

    @Test
    fun `is true criteria should produce correct string`() {
        assertThat(Criteria.IsTrue("active").toString()).isEqualTo("active IS TRUE")
    }

    @Test
    fun `is false criteria should produce correct string`() {
        assertThat(Criteria.IsFalse("active").toString()).isEqualTo("active IS FALSE")
    }

    @Test
    fun `nested and or should produce correct string`() {
        val criteria = Criteria.And(
            listOf(
                Criteria.Equals("type", "post"),
                Criteria.Or(
                    listOf(
                        Criteria.Equals("status", "draft"),
                        Criteria.Equals("status", "published"),
                    ),
                ),
            ),
        )

        assertThat(criteria.toString()).isEqualTo("(type = post AND (status = draft OR status = published))")
    }
}
