package com.profiletailors.smp.shortlinks.infrastructure.codegen

import com.profiletailors.smp.shortlinks.domain.ShortCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SecureRandomShortCodeGeneratorTest {
    private val base62 = Regex("^[0-9A-Za-z]+$")
    private val generator = SecureRandomShortCodeGenerator()

    @Test
    fun `generates default length base62 codes`() {
        val code = generator.generate()

        assertEquals(ShortCode.DEFAULT_LENGTH, code.value.length)
        assertTrue(base62.matches(code.value))
    }

    @Test
    fun `generates distinct codes`() {
        val codes = (1..20).map { generator.generate().value }.toSet()

        assertTrue(codes.size > 1)
    }
}
