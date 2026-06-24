package com.profiletailors.common.domain.presentation.pagination

import com.profiletailors.common.domain.error.BusinessRuleValidationException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class InvalidCursorTest {

    @Test
    fun `should create with message`() {
        val error = InvalidCursor("Bad cursor")

        assertThat(error.message).isEqualTo("Bad cursor")
        assertThat(error.cause).isNull()
    }

    @Test
    fun `should create with message and cause`() {
        val cause = RuntimeException("underlying error")
        val error = InvalidCursor("Bad cursor", cause)

        assertThat(error.message).isEqualTo("Bad cursor")
        assertThat(error.cause).isSameAs(cause)
    }

    @Test
    fun `should extend BusinessRuleValidationException`() {
        val error = InvalidCursor("error")

        assertThat(error).isInstanceOf(BusinessRuleValidationException::class.java)
    }
}
