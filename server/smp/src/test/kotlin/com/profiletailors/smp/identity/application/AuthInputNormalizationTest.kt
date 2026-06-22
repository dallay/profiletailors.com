package com.profiletailors.smp.identity.application

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthInputNormalizationTest {

    @Test
    fun `normalizeEmail trims and lowercases`() {
        assertEquals("yuniel@example.com", normalizeEmail(" Yuniel@Example.com "))
    }

    @Test
    fun `normalizeUsername trims and keeps non-empty value`() {
        assertEquals("yuniel", normalizeUsername(" yuniel ", "yuniel@example.com"))
    }

    @Test
    fun `normalizeUsername falls back to email local-part when blank`() {
        assertEquals("yuniel", normalizeUsername("   ", "yuniel@example.com"))
    }

    @Test
    fun `normalizeUsername falls back to email local-part when null`() {
        assertEquals("yuniel", normalizeUsername(null, "yuniel@example.com"))
    }

    @Test
    fun `normalizeOptionalUsername trims and returns null when blank`() {
        assertNull(normalizeOptionalUsername("   "))
    }

    @Test
    fun `normalizeOptionalUsername trims and keeps non-empty value`() {
        assertEquals("display-name", normalizeOptionalUsername(" display-name "))
    }
}
