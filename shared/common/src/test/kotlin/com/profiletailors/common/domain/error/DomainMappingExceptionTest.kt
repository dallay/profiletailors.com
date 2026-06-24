package com.profiletailors.common.domain.error

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class DomainMappingExceptionTest {

    @Test
    fun `should create with message only`() {
        val exception = DomainMappingException("Mapping failed")

        assertThat(exception.message).isEqualTo("Mapping failed")
        assertThat(exception.cause).isNull()
    }

    @Test
    fun `should create with message and cause`() {
        val cause = RuntimeException("underlying error")
        val exception = DomainMappingException("Mapping failed", cause)

        assertThat(exception.message).isEqualTo("Mapping failed")
        assertThat(exception.cause).isSameAs(cause)
    }

    @Test
    fun `should be a RuntimeException`() {
        val exception = DomainMappingException("error")

        assertThat(exception).isInstanceOf(RuntimeException::class.java)
    }
}
