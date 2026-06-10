package com.profiletailors.common.domain.security

interface HasherSecurityConfig {
    val ipHmacSecret: String
    val allowInsecureHasher: Boolean
}
