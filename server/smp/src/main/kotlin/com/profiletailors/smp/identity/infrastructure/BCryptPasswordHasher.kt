package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.application.PasswordHasher
import org.springframework.security.crypto.bcrypt.BCrypt

class BCryptPasswordHasher : PasswordHasher {
    /**
 * Hashes a raw password using BCrypt with a generated salt.
 *
 * @param rawPassword The password to hash.
 * @return The BCrypt password hash.
 */
override fun hash(rawPassword: String): String = BCrypt.hashpw(rawPassword, BCrypt.gensalt())

    /**
     * Verifies a raw password against a BCrypt password hash.
     *
     * @param rawPassword The password to verify.
     * @param passwordHash The BCrypt hash to verify against.
     * @return `true` if the password matches the hash, `false` otherwise or if the hash is malformed.
     */
    override fun matches(rawPassword: String, passwordHash: String): Boolean = try {
        BCrypt.checkpw(rawPassword, passwordHash)
    } catch (_: IllegalArgumentException) {
        // Malformed hash (e.g., dev placeholder) — fail secure instead of crashing
        false
    }

    override val algorithm: String = "bcrypt"
}
