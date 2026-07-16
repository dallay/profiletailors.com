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
    fun `source with valid alphanumeric and hyphens is accepted`() {
        val source = CaptureSource("landing-page-2024")
        assertEquals("landing-page-2024", source.value)
    }

    @Test
    fun `source with unsupported characters is rejected`() {
        assertThrows<IllegalArgumentException> { CaptureSource("marketing_homepage") }
        assertThrows<IllegalArgumentException> { CaptureSource("landing page") }
        assertThrows<IllegalArgumentException> { CaptureSource("email@campaign") }
        assertThrows<IllegalArgumentException> { CaptureSource("promo/banner") }
    }

    @Test
    fun `source exceeding 50 characters is rejected`() {
        val tooLong = "a".repeat(51)
        assertThrows<IllegalArgumentException> { CaptureSource(tooLong) }
    }

    @Test
    fun `source at exactly 50 characters is accepted`() {
        val exactly50 = "a".repeat(50)
        val source = CaptureSource(exactly50)
        assertEquals(exactly50, source.value)
    }

    @Test
    fun `equality is value-based`() {
        val s1 = CaptureSource("landing-pricing")
        val s2 = CaptureSource("landing-pricing")
        assertEquals(s1, s2)
        assertEquals(s1.hashCode(), s2.hashCode())
    }
}
