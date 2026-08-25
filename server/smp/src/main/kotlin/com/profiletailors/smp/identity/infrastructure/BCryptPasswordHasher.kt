package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.application.PasswordHasher
import org.springframework.security.crypto.bcrypt.BCrypt
import java.security.MessageDigest

class BCryptPasswordHasher : PasswordHasher {
    /**
         * Hashes a password using BCrypt with a cost factor of 12.
         *
         * @param rawPassword The password to hash.
         * @return The BCrypt password hash.
         */
        override fun hash(rawPassword: String): String =
        BCrypt.hashpw(bcryptInput(rawPassword), BCrypt.gensalt(COST_FACTOR))

    /**
     * Verifies a raw password against a stored BCrypt password hash.
     *
     * @param rawPassword The password to verify.
     * @param passwordHash The stored BCrypt password hash.
     * @return `true` if the password matches the hash, `false` otherwise.
     */
    override fun matches(rawPassword: String, passwordHash: String): Boolean = try {
        BCrypt.checkpw(bcryptInput(rawPassword), passwordHash)
    } catch (_: IllegalArgumentException) {
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
        const val COST_FACTOR = 12
    }
}
