package com.profiletailors.smp.identity.application

interface PasswordHasher {
    fun hash(rawPassword: String): String
    fun matches(rawPassword: String, passwordHash: String): Boolean
    val algorithm: String
}
