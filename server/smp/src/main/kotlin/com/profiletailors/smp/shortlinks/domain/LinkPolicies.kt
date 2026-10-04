package com.profiletailors.smp.shortlinks.domain

import java.time.Duration

object LinkPolicies {
    val RESERVED_ALIASES: Set<String> = setOf(
        "api",
        "admin",
        "login",
        "logout",
        "signup",
        "register",
        "health",
        "metrics",
        "docs",
        "robots.txt",
        "favicon.ico",
    )

    val DELETED_CODE_RETENTION: Duration = Duration.ofDays(90)

    const val MAX_COLLISION_RETRIES = 5
}
