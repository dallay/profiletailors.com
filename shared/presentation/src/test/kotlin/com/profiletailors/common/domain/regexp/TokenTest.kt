package com.profiletailors.common.domain.regexp

import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.Test

class TokenTest {
    @Test
    fun testEscapeToken() {
        val escapeToken = EscapeToken("abc")
        assertEquals("\\Qabc\\E", escapeToken.convert())
    }

    @Test
    fun testWildcardToken() {
        val wildcardToken = WildcardToken("def")
        assertEquals(".*", wildcardToken.convert())
    }

    @Test
    fun testWildcharToken() {
        val wildcharToken = WildcharToken("xyz")
        assertEquals(".", wildcharToken.convert())
    }

    @Test
    fun testStringToken() {
        val stringToken = StringToken("123")
        assertEquals("\\Q123\\E", stringToken.convert())
    }
}
