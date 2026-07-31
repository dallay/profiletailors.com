package com.profiletailors.smp.platformadmin.infrastructure

import com.profiletailors.smp.platformadmin.application.ports.TokenHasher
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class BCryptTokenHasher : TokenHasher {
    private val delegate = BCryptPasswordEncoder()

    override fun hash(rawToken: String): String = delegate.encode(rawToken)!!

    override fun matches(rawToken: String, storedHash: String): Boolean = delegate.matches(rawToken, storedHash)
}
