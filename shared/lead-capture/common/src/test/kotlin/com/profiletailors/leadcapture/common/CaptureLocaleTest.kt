package com.profiletailors.leadcapture.common

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

internal class CaptureLocaleTest {

    @Test
    fun `valid locale is accepted`() {
        val locale = CaptureLocale("en")
        assertEquals("en", locale.value)
    }

    @Test
    fun `valid locale with region is accepted`() {
        val locale = CaptureLocale("es-ES")
        assertEquals("es-ES", locale.value)
    }

    @Test
    fun `blank locale is rejected`() {
        assertThrows<IllegalArgumentException> { CaptureLocale("") }
    }

    @Test
    fun `equality is value-based`() {
        val l1 = CaptureLocale("es")
        val l2 = CaptureLocale("es")
        assertEquals(l1, l2)
        assertEquals(l1.hashCode(), l2.hashCode())
    }
}
