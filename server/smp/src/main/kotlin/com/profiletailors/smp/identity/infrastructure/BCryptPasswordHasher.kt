package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.application.PasswordHasher
import org.springframework.security.crypto.bcrypt.BCrypt

class BCryptPasswordHasher : PasswordHasher {
    override fun hash(rawPassword: String): String = BCrypt.hashpw(rawPassword, BCrypt.gensalt())

    override fun matches(rawPassword: String, passwordHash: String): Boolean =
        BCrypt.checkpw(rawPassword, passwordHash)
}
