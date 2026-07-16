package com.profiletailors.leadcapture.common

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

internal class CaptureSourceTest {

    @Test
    fun `valid source is accepted`() {
        val source = CaptureSource("marketing-homepage")
        assertEquals("marketing-homepage", source.value)
    }

    @Test
    fun `blank source is rejected`() {
        assertThrows<IllegalArgumentException> { CaptureSource("") }
        assertThrows<IllegalArgumentException> { CaptureSource("   ") }
    }

    @Test
    fun `equality is value-based`() {
        val s1 = CaptureSource("landing-pricing")
        val s2 = CaptureSource("landing-pricing")
        assertEquals(s1, s2)
        assertEquals(s1.hashCode(), s2.hashCode())
    }
}
