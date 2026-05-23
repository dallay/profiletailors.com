package com.profiletailors.smp.credentials.infrastructure

import com.profiletailors.smp.credentials.application.RefreshTokenHasher
import org.springframework.security.crypto.bcrypt.BCrypt

class BCryptRefreshTokenHasher : RefreshTokenHasher {
    override fun hash(secret: String): String = BCrypt.hashpw(secret, BCrypt.gensalt())

    override fun matches(presentedSecret: String, storedVerifier: String): Boolean =
        BCrypt.checkpw(presentedSecret, storedVerifier)
}
