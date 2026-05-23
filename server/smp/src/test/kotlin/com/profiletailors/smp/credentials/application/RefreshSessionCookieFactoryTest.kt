package com.profiletailors.smp.credentials.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RefreshSessionCookieFactoryTest {

    @Test
    fun `builds set and clear cookies with configured policy`() {
        val factory = RefreshSessionCookieFactory(
            RefreshSessionProperties(
                cookieName = "pt_refresh",
                cookiePath = "/api/auth",
                sameSite = "Lax",
                secure = false,
                ttlSeconds = 604800,
            ),
        )

        val setCookie = factory.buildSetCookie(RefreshSessionToken("lookup", "secret"))
        val clearCookie = factory.buildClearCookie()

        assertEquals("pt_refresh", setCookie.name)
        assertEquals("lookup.secret", setCookie.value)
        assertEquals("/api/auth", setCookie.path)
        assertEquals(604800L, setCookie.maxAge.seconds)
        assertEquals("pt_refresh", clearCookie.name)
        assertTrue(clearCookie.maxAge.isZero)
    }
}
