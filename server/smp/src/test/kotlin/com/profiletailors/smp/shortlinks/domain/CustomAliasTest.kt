package com.profiletailors.smp.shortlinks.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

internal class CustomAliasTest {
    @Test
    fun `accepts base62 alias and converts to short code`() {
        val alias = CustomAlias("AbC123xYz")

        assertEquals(ShortCode("AbC123xYz"), alias.toShortCode())
    }

    @Test
    fun `rejects underscore`() {
        assertThrows(IllegalArgumentException::class.java) { CustomAlias("ab_cd") }
    }

    @Test
    fun `rejects hyphen`() {
        assertThrows(IllegalArgumentException::class.java) { CustomAlias("ab-cd") }
    }

    @Test
    fun `rejects short alias`() {
        assertThrows(IllegalArgumentException::class.java) { CustomAlias("abc") }
    }

    @Test
    fun `rejects reserved alias`() {
        assertThrows(IllegalArgumentException::class.java) { CustomAlias("admin") }
    }
}
