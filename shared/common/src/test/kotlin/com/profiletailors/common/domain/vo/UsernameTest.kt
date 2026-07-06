package com.profiletailors.common.domain.vo

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.test.assertNull

class UsernameTest {
    @Test
    fun `should create valid username`() {
        val username = Username("johndoe")
        username.value shouldBe "johndoe"
    }

    @Test
    fun `should throw on empty username`() {
        shouldThrow<IllegalArgumentException> { Username("") }
        shouldThrow<IllegalArgumentException> { Username("  ") }
    }

    @Test
    fun `should throw on too short or too long username`() {
        shouldThrow<IllegalArgumentException> { Username("jo") }
        shouldThrow<IllegalArgumentException> { Username("a".repeat(101)) }
    }

    @Test
    fun `of should return null for invalid input`() {
        assertNull(Username.of(""))
        assertNull(Username.of("jo"))
        Username.of("johndoe")?.value shouldBe "johndoe"
    }
}
