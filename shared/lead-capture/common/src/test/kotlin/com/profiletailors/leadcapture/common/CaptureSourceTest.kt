package com.profiletailors.leadcapture.common

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

internal class CaptureSourceTest {

    @Test
    fun `valid alphanumeric and hyphen source is accepted`() {
        val source = CaptureSource("marketing-homepage")
        assertEquals("marketing-homepage", source.value)
    }

    @Test
    fun `blank source is rejected`() {
        assertThrows<IllegalArgumentException> { CaptureSource("") }
        assertThrows<IllegalArgumentException> { CaptureSource("   ") }
    }

    @Test
    fun `source with unsupported characters is rejected`() {
        assertThrows<IllegalArgumentException> { CaptureSource("hello world") }
        assertThrows<IllegalArgumentException> { CaptureSource("hello_world") }
        assertThrows<IllegalArgumentException> { CaptureSource("hello/world") }
        assertThrows<IllegalArgumentException> { CaptureSource("hello.world") }
    }

    @Test
    fun `source exceeding 50 characters is rejected`() {
        val long = "a".repeat(51)
        assertThrows<IllegalArgumentException> { CaptureSource(long) }
    }

    @Test
    fun `source at exactly 50 characters is accepted`() {
        val exact = "a".repeat(50)
        val source = CaptureSource(exact)
        assertEquals(exact, source.value)
    }

    @Test
    fun `equality is value-based`() {
        val s1 = CaptureSource("landing-pricing")
        val s2 = CaptureSource("landing-pricing")
        assertEquals(s1, s2)
        assertEquals(s1.hashCode(), s2.hashCode())
    }
}
