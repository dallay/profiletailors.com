package com.profiletailors.smp.credentials.domain

import com.profiletailors.common.domain.ValueObject

@ValueObject
data class SessionCookie(
    val name: String,
    val value: String,
    val path: String,
    val sameSite: String,
    val secure: Boolean,
    val httpOnly: Boolean,
    val maxAgeSeconds: Long,
) {
    init {
        require(name.isNotBlank()) { "Session cookie name must not be blank." }
        require(path.isNotBlank()) { "Session cookie path must not be blank." }
    }
}
