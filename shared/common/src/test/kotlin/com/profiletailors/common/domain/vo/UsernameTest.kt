package com.profiletailors.common.domain.vo

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

internal class UsernameTest {
    @Test
    fun `should create a valid username`() {
        val username = Username("john_doe")
        assertEquals("john_doe", username.value)
    }

    @Test
    fun `should throw exception for blank username`() {
        assertThrows(IllegalArgumentException::class.java) {
            Username(" ")
        }
    }

    @Test
    fun `should throw exception for too short username`() {
        assertThrows(IllegalArgumentException::class.java) {
            Username("ab")
        }
    }

    @Test
    fun `should throw exception for too long username`() {
        val longUsername = "a".repeat(101)
        assertThrows(IllegalArgumentException::class.java) {
            Username(longUsername)
        }
    }

    @Test
    fun `should return null from of() for blank input`() {
        assertThat(Username.of(" ")).isNull()
    }

    @Test
    fun `should return null from of() for too short input`() {
        assertThat(Username.of("ab")).isNull()
    }

    @Test
    fun `should create Username via of() for valid input`() {
        assertThat(Username.of("jane_doe")).isEqualTo(Username("jane_doe"))
    }

    @Test
    fun `compare two equal usernames`() {
        val username1 = Username("john_doe")
        val username2 = Username("john_doe")
        assertEquals(username1, username2)
        assertEquals(username1.hashCode(), username2.hashCode())
    }

    @Test
    fun `compare two different usernames`() {
        val username1 = Username("john_doe")
        val username2 = Username("jane_doe")
        assertNotEquals(username1, username2)
        assertNotEquals(username1.hashCode(), username2.hashCode())
    }
}
