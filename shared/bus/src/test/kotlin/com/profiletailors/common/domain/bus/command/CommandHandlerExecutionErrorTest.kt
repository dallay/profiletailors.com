package com.profiletailors.common.domain.bus.command

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class CommandHandlerExecutionErrorTest {

    @Test
    fun `should create with default null message`() {
        val error = CommandHandlerExecutionError()

        assertThat(error.message).isNull()
        assertThat(error.cause).isNull()
    }

    @Test
    fun `should create with message`() {
        val error = CommandHandlerExecutionError("Handler failed")

        assertThat(error.message).isEqualTo("Handler failed")
    }

    @Test
    fun `should create with message and cause`() {
        val cause = RuntimeException("db error")
        val error = CommandHandlerExecutionError("Handler failed", cause)

        assertThat(error.message).isEqualTo("Handler failed")
        assertThat(error.cause).isSameAs(cause)
    }

    @Test
    fun `should be a RuntimeException`() {
        val error = CommandHandlerExecutionError()

        assertThat(error).isInstanceOf(RuntimeException::class.java)
    }
}
