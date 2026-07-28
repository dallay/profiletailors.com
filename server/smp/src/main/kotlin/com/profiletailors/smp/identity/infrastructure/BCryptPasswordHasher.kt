package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.application.PasswordHasher
import org.springframework.security.crypto.bcrypt.BCrypt
import java.security.MessageDigest

class BCryptPasswordHasher : PasswordHasher {
    override fun hash(rawPassword: String): String = BCrypt.hashpw(bcryptInput(rawPassword), BCrypt.gensalt())

    override fun matches(rawPassword: String, passwordHash: String): Boolean = try {
        BCrypt.checkpw(bcryptInput(rawPassword), passwordHash)
    } catch (_: IllegalArgumentException) {
        // Malformed hash (e.g., dev placeholder) — fail secure instead of crashing
        false
    }

    override val algorithm: String = "bcrypt"

    private fun bcryptInput(rawPassword: String): String = if (rawPassword.toByteArray().size <= BCRYPT_MAX_BYTES) {
        rawPassword
    } else {
        MessageDigest.getInstance("SHA-256")
            .digest(rawPassword.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val BCRYPT_MAX_BYTES = 72
    }
}
