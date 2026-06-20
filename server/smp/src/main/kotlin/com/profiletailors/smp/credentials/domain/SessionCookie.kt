package com.profiletailors.smp.credentials.domain

data class SessionCookie(
    val name: String,
    val value: String,
    val path: String,
    val sameSite: String,
    val secure: Boolean,
    val httpOnly: Boolean,
    val maxAgeSeconds: Long,
)
