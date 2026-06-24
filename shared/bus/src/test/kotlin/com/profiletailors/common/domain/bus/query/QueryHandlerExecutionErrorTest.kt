package com.profiletailors.common.domain.bus.query

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class QueryHandlerExecutionErrorTest {

    @Test
    fun `should create with cause`() {
        val cause = RuntimeException("query failed")

        val error = QueryHandlerExecutionError(cause)

        assertThat(error.cause).isSameAs(cause)
    }

    @Test
    fun `should include cause message`() {
        val cause = RuntimeException("query failed")
        val error = QueryHandlerExecutionError(cause)

        assertThat(error.message).contains("query failed")
    }

    @Test
    fun `should be a RuntimeException`() {
        val cause = RuntimeException("error")
        val error = QueryHandlerExecutionError(cause)

        assertThat(error).isInstanceOf(RuntimeException::class.java)
    }
}
