package com.profiletailors.common.domain.error

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class EntityNotFoundExceptionTest {

    @Test
    fun `should create with message`() {
        val exception = TestEntityNotFoundException("User not found")

        assertThat(exception.message).isEqualTo("User not found")
        assertThat(exception.cause).isNull()
    }

    @Test
    fun `should create with message and cause`() {
        val cause = RuntimeException("db error")
        val exception = TestEntityNotFoundException("User not found", cause)

        assertThat(exception.message).isEqualTo("User not found")
        assertThat(exception.cause).isSameAs(cause)
    }

    @Test
    fun `should extend BusinessRuleValidationException`() {
        val exception = TestEntityNotFoundException("error")

        assertThat(exception).isInstanceOf(BusinessRuleValidationException::class.java)
    }

    private class TestEntityNotFoundException(message: String, cause: Throwable? = null) :
        EntityNotFoundException(message, cause)
}
