package com.profiletailors.common.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

internal class LanguageTest {
    @Test
    fun `should return ENGLISH for en`() {
        assertEquals(Language.ENGLISH, Language.fromString("en"))
    }

    @Test
    fun `should return SPANISH for es`() {
        assertEquals(Language.SPANISH, Language.fromString("es"))
    }

    @Test
    fun `should be case insensitive`() {
        assertEquals(Language.ENGLISH, Language.fromString("EN"))
        assertEquals(Language.ENGLISH, Language.fromString("En"))
        assertEquals(Language.SPANISH, Language.fromString("ES"))
        assertEquals(Language.SPANISH, Language.fromString("eS"))
    }

    @Test
    fun `should throw exception for invalid code`() {
        assertThrows(IllegalArgumentException::class.java) {
            Language.fromString("fr")
        }

        assertThrows(IllegalArgumentException::class.java) {
            Language.fromString("de")
        }
    }

    @Test
    fun `should throw exception for null code`() {
        assertThrows(IllegalArgumentException::class.java) {
            Language.fromString(null)
        }
    }

    @Test
    fun `should have correct codes`() {
        assertEquals("en", Language.ENGLISH.code)
        assertEquals("es", Language.SPANISH.code)
    }
}
