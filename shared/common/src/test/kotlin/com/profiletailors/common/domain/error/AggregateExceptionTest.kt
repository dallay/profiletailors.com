package com.profiletailors.common.domain.error

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class AggregateExceptionTest {
    @Test
    fun `should create with collection of exceptions`() {
        val exceptions = listOf(
            RuntimeException("error 1"),
            RuntimeException("error 2"),
            RuntimeException("error 3"),
        )
        val aggregate = AggregateException(exceptions)
        assertThat(aggregate.exceptions).hasSize(3)
    }

    @Test
    fun `should create with array of exceptions`() {
        val exceptions = arrayOf<Throwable>(
            RuntimeException("error 1"),
            RuntimeException("error 2"),
        )
        val aggregate = AggregateException(exceptions)
        assertThat(aggregate.exceptions).hasSize(2)
    }

    @Test
    fun `should preserve all exceptions`() {
        val exceptions = listOf(
            RuntimeException("error 1"),
            RuntimeException("error 2"),
            RuntimeException("error 3"),
        )
        val aggregate = AggregateException(exceptions)
        assertThat(aggregate.exceptions).containsExactlyElementsOf(exceptions)
    }

    @Test
    fun `should handle empty collection`() {
        val exceptions = emptyList<Throwable>()
        val aggregate = AggregateException(exceptions)
        assertThat(aggregate.exceptions).isEmpty()
    }

    @Test
    fun `should handle single exception`() {
        val exceptions = listOf(RuntimeException("only error"))
        val aggregate = AggregateException(exceptions)
        assertThat(aggregate.exceptions).hasSize(1)
        assertThat(aggregate.exceptions.single().message).isEqualTo("only error")
    }
}
