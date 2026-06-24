package com.profiletailors.common.domain.presentation.pagination

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class RequestPageableTest {

    @Test
    fun `offset request pageable should have default values`() {
        val request = OffsetRequestPageable()

        assertThat(request.size).isEqualTo(10)
        assertThat(request.search).isNull()
        assertThat(request.filter).isEmpty()
        assertThat(request.sort).isEmpty()
        assertThat(request.page).isEqualTo(0)
    }

    @Test
    fun `cursor request pageable should have default values`() {
        val request = CursorRequestPageable()

        assertThat(request.size).isEqualTo(10)
        assertThat(request.search).isNull()
        assertThat(request.filter).isEmpty()
        assertThat(request.sort).isEmpty()
        assertThat(request.cursor).isNull()
    }

    @Test
    fun `offset request pageable should support custom values`() {
        val request = OffsetRequestPageable(
            size = 25,
            search = "test",
            page = 2,
        )

        assertThat(request.size).isEqualTo(25)
        assertThat(request.search).isEqualTo("test")
        assertThat(request.page).isEqualTo(2)
    }

    @Test
    fun `filter condition should format correctly`() {
        val condition = FilterCondition(LogicalOperator.AND, listOf("value1", "value2"))

        assertThat(condition.toString()).isEqualTo("AND:value1,value2")
    }
}
