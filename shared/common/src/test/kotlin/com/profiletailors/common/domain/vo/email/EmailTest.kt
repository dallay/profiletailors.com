package com.profiletailors.common.domain.vo.email

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.test.assertNull

class EmailTest {
    @Test
    fun `should create valid email`() {
        val email = Email("test@example.com")
        email.value shouldBe "test@example.com"
    }

    @Test
    fun `should throw on invalid email format`() {
        shouldThrow<IllegalArgumentException> { Email("invalid-email") }
        shouldThrow<IllegalArgumentException> { Email("test@") }
        shouldThrow<IllegalArgumentException> { Email("@example.com") }
    }

    @Test
    fun `of should return null for invalid input`() {
        assertNull(Email.of("invalid"))
        Email.of("test@example.com")?.value shouldBe "test@example.com"
    }
}
