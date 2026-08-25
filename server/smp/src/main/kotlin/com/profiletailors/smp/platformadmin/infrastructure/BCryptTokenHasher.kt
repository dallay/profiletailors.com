package com.profiletailors.smp.platformadmin.infrastructure

import com.profiletailors.smp.platformadmin.application.contracts.InvitationTokenCandidateKey
import com.profiletailors.smp.platformadmin.application.contracts.TokenHasher
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class BCryptTokenHasher :
    TokenHasher,
    InvitationTokenCandidateKey {
    private val delegate = BCryptPasswordEncoder()

    override fun hash(rawToken: String): String =
        checkNotNull(delegate.encode(rawToken)) { "BCrypt encoding must not produce a null hash" }

    override fun matches(rawToken: String, storedHash: String): Boolean = delegate.matches(rawToken, storedHash)

    override fun candidateKey(rawToken: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(rawToken.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
