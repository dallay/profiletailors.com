package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.application.PasswordHasher
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder

class Argon2idPasswordHasher : PasswordHasher {

    private val encoder: Argon2PasswordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()

    override val algorithm: String = "argon2id"

    override fun hash(rawPassword: String): String = requireNotNull(encoder.encode(rawPassword))

    override fun matches(rawPassword: String, passwordHash: String): Boolean = try {
        encoder.matches(rawPassword, passwordHash)
    } catch (_: IllegalArgumentException) {
        false
    }
}
