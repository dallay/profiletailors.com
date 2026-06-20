package com.profiletailors.smp.credentials.application

import com.profiletailors.smp.credentials.application.RefreshSessionProperties
import com.profiletailors.smp.credentials.application.RefreshSessionToken
import com.profiletailors.smp.credentials.infrastructure.RefreshSessionCookieFactory
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
        assertEquals(604800L, setCookie.maxAgeSeconds)
        assertTrue(setCookie.httpOnly)
        assertEquals("Lax", setCookie.sameSite)
        assertEquals(false, setCookie.secure)
        assertEquals("pt_refresh", clearCookie.name)
        assertEquals(0L, clearCookie.maxAgeSeconds)
    }
}
