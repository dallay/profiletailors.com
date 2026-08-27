package com.profiletailors.common.domain.vo.name

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

internal class LastNameTest {
    @Test
    fun `should create a valid last name`() {
        val lastNames = listOf(
            "Acosta",
            "Acosta Ortiz",
            "Acosta Pérez",
            "O'Neil",
            "D'Angelo",
        )
        lastNames.forEach {
            val lastName = LastName(it)
            assertEquals(it, lastName.value)
        }
    }

    @Test
    fun `should throw an exception when create a last name with invalid value`() {
        val invalidLastNames = listOf(
            """
                A voluptatum ex. Ratione adipisci eveniet expedita iste consectetur voluptatibus illum odio optio.
                Eum facilis autem. Explicabo sequi corrupti eius quis eius adipisci.
            """.trimIndent(),
        )
        invalidLastNames.forEach {
            assertThrows(IllegalArgumentException::class.java) {
                LastName(it)
            }
        }
    }

    @Test
    fun `should throw an exception when create a last name with empty value`() {
        assertThrows(IllegalArgumentException::class.java) {
            LastName("")
        }
    }

    @Test
    fun `should throw an exception when create a last name with blank value`() {
        assertThrows(IllegalArgumentException::class.java) {
            LastName(" ")
        }
    }

    @Test
    fun `should throw an exception when create a last name with length greater than 150`() {
        val lastName = (1..256).joinToString("") { "a" }
        assertThrows(IllegalArgumentException::class.java) {
            LastName(lastName)
        }
    }

    @Test
    fun `compare last name`() {
        val lastName1 = LastName("Acosta")
        val lastName2 = LastName("Acosta")
        assertEquals(lastName1, lastName2)
    }

    @Test
    fun `compare last name with different values`() {
        val lastName1 = LastName("Acosta")
        val lastName2 = LastName("Acosta Ortiz")
        assertNotEquals(lastName1, lastName2)
        assertNotEquals(lastName1.hashCode(), lastName2.hashCode())
    }
}
