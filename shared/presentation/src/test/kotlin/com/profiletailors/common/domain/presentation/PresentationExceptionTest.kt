package com.profiletailors.common.domain.presentation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PresentationExceptionTest {

    @Test
    fun `invalid request should create with message`() {
        val ex = InvalidRequestException("Bad request")

        assertThat(ex.message).isEqualTo("Bad request")
        assertThat(ex).isInstanceOf(RuntimeException::class.java)
    }

    @Test
    fun `sort invalid should create with message`() {
        val ex = SortInvalidException("Invalid sort field")

        assertThat(ex.message).isEqualTo("Invalid sort field")
        assertThat(ex).isInstanceOf(InvalidRequestException::class.java)
    }

    @Test
    fun `filter invalid should create with message`() {
        val ex = FilterInvalidException("Invalid filter")

        assertThat(ex.message).isEqualTo("Invalid filter")
        assertThat(ex).isInstanceOf(InvalidRequestException::class.java)
    }

    @Test
    fun `sort invalid should be throwable as InvalidRequestException`() {
        val ex: InvalidRequestException = SortInvalidException("error")

        assertThat(ex).isInstanceOf(SortInvalidException::class.java)
    }
}
