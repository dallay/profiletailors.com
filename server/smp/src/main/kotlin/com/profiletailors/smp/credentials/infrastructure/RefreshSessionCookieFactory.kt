package com.profiletailors.smp.credentials.infrastructure

import com.profiletailors.smp.credentials.application.RefreshSessionProperties
import com.profiletailors.smp.credentials.application.RefreshSessionToken
import com.profiletailors.smp.credentials.domain.SessionCookie

class RefreshSessionCookieFactory(
    private val properties: RefreshSessionProperties,
) {
    fun buildSetCookie(refreshToken: RefreshSessionToken): SessionCookie = SessionCookie(
        name = properties.cookieName,
        value = refreshToken.asCookieValue(),
        path = properties.cookiePath,
        sameSite = properties.sameSite,
        secure = properties.secure,
        httpOnly = true,
        maxAgeSeconds = properties.ttlSeconds,
    )

    fun buildClearCookie(): SessionCookie = SessionCookie(
        name = properties.cookieName,
        value = "",
        path = properties.cookiePath,
        sameSite = properties.sameSite,
        secure = properties.secure,
        httpOnly = true,
        maxAgeSeconds = 0,
    )
}
