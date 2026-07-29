package com.profiletailors.common.domain.vo.name

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

internal class FirstNameTest {
    @Test
    fun `should create a valid first name`() {
        val firstName = FirstName("Yuniel")
        assertEquals("Yuniel", firstName.value)
    }

    @Test
    fun `should throw an exception when create a first name with empty value`() {
        assertThrows(IllegalArgumentException::class.java) {
            FirstName("")
        }
    }

    @Test
    fun `should throw an exception when create a first name with blank value`() {
        assertThrows(IllegalArgumentException::class.java) {
            FirstName(" ")
        }
    }

    @Test
    fun `should throw an exception when create a first name with length greater than maximum`() {
        val firstName = "a".repeat(51)
        assertThrows(IllegalArgumentException::class.java) {
            FirstName(firstName)
        }
    }

    @Test
    fun `compare first name`() {
        val firstName1 = FirstName("Yuniel")
        val firstName2 = FirstName("Yuniel")
        assertEquals(firstName1, firstName2)
        assertEquals(firstName1.hashCode(), firstName2.hashCode())
    }

    @Test
    fun `compare first name with different values`() {
        val firstName1 = FirstName("Yuniel")
        val firstName2 = FirstName("David")
        assertNotEquals(firstName1, firstName2)
        assertNotEquals(firstName1.hashCode(), firstName2.hashCode())
    }
}
