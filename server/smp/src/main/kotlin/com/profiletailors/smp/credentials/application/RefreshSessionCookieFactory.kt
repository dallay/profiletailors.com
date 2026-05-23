package com.profiletailors.smp.credentials.application

import org.springframework.http.ResponseCookie
import java.time.Duration

class RefreshSessionCookieFactory(
    private val properties: RefreshSessionProperties,
) {
    fun buildSetCookie(refreshToken: RefreshSessionToken): ResponseCookie =
        ResponseCookie.from(properties.cookieName, refreshToken.asCookieValue())
        .httpOnly(true)
        .secure(properties.secure)
        .sameSite(properties.sameSite)
        .path(properties.cookiePath)
        .maxAge(Duration.ofSeconds(properties.ttlSeconds))
        .build()

    fun buildClearCookie(): ResponseCookie = ResponseCookie.from(properties.cookieName, "")
        .httpOnly(true)
        .secure(properties.secure)
        .sameSite(properties.sameSite)
        .path(properties.cookiePath)
        .maxAge(Duration.ZERO)
        .build()
}

data class RefreshSessionProperties(
    val cookieName: String,
    val cookiePath: String,
    val sameSite: String,
    val secure: Boolean,
    val ttlSeconds: Long,
)
