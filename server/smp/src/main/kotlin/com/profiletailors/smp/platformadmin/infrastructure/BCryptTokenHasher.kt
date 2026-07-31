package com.profiletailors.smp.platformadmin.infrastructure

import com.profiletailors.smp.platformadmin.application.ports.TokenHasher
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class BCryptTokenHasher : TokenHasher {
    private val delegate = BCryptPasswordEncoder()

    override fun hash(rawToken: String): String =
        checkNotNull(delegate.encode(rawToken)) { "BCrypt encoding must not produce a null hash" }

    override fun matches(rawToken: String, storedHash: String): Boolean = delegate.matches(rawToken, storedHash)
}
