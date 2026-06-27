package com.profiletailors.common.domain.vo.email

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

internal class EmailTest {
    @Test
    fun `should create email`() {
        val email = Email("john.snow@gmail.com")
        assertEquals("john.snow@gmail.com", email.value)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "john.snow@gmail.", "Julia.abc@",
            "Julia.abc@.com", "Samantha_21.", ".1Samantha",
            "Samantha@10_2A", "JuliaZ007", "_Julia007.com",
            "Willie_Zboncak@@yahoo.com",
        ],
    )
    fun `should throw exception when email is not valid`(invalidEmail: String) {
        assertThrows(IllegalArgumentException::class.java) {
            Email(invalidEmail)
        }
    }

    @Test
    fun `should throw exception when email is empty`() {
        assertThrows(IllegalArgumentException::class.java) {
            Email("")
        }
    }

    @Test
    fun `should get null from blank email`() {
        assertThat(Email.of(" ")).isNull()
    }

    @Test
    fun `should get email from actual email`() {
        assertThat(Email.of("user@example.com")).isEqualTo(Email("user@example.com"))
    }

    @Test
    fun `should get email value`() {
        assertThat(Email("user@example.com").value).isEqualTo("user@example.com")
    }

    @Test
    fun `should get null from invalid email`() {
        assertThat(Email.of("invalid-email")).isNull()
    }

    @Test
    fun `should throw exception when email is blank`() {
        val instanceOf =
            assertThatThrownBy { Email(" ") }.isInstanceOf(IllegalArgumentException::class.java)
        instanceOf.hasMessage("Email cannot be blank")
    }

    @Test
    fun `should throw exception when email is invalid`() {
        val instanceOf = assertThatThrownBy { Email("invalid-email") }
            .isInstanceOf(IllegalArgumentException::class.java)
        instanceOf.hasMessage("Email is not valid: invalid-email")
    }

    @Test
    fun `should throw exception when email is too long`() {
        val longEmail = "a".repeat(310) + "@example.com"
        val instanceOf =
            assertThatThrownBy { Email(longEmail) }.isInstanceOf(IllegalArgumentException::class.java)
        instanceOf.hasMessage("Email cannot exceed 320 characters")
    }

    @Test
    fun `compare email`() {
        val email1 = Email("john.snow@gmail.com")
        val email2 = Email("john.snow@gmail.com")
        assertEquals(email1, email2)
        assertEquals(email1.hashCode(), email2.hashCode())
    }

    @Test
    fun `compare email with different value`() {
        val email1 = Email("john.snow@gmail.com")
        val email2 = Email("john-snow@gmail.com")
        assertNotEquals(email1, email2)
        assertNotEquals(email1.hashCode(), email2.hashCode())
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            ".user@example.com",
            "user.@example.com",
            "user..name@example.com",
        ],
    )
    fun `should throw exception for emails with invalid dots`(invalidEmail: String) {
        assertThrows(IllegalArgumentException::class.java) {
            Email(invalidEmail)
        }
    }
}
