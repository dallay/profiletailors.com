package com.profiletailors.smp.credentials.application

data class RefreshSessionProperties(
    val cookieName: String,
    val cookiePath: String,
    val sameSite: String,
    val secure: Boolean,
    val ttlSeconds: Long,
)
