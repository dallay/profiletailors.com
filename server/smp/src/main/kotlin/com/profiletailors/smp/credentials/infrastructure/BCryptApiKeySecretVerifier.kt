package com.profiletailors.smp.credentials.infrastructure

import com.profiletailors.smp.credentials.application.ApiKeySecretVerifier
import org.springframework.security.crypto.bcrypt.BCrypt

class BCryptApiKeySecretVerifier : ApiKeySecretVerifier {
    override fun matches(presentedSecret: String, storedVerifier: String): Boolean =
        BCrypt.checkpw(presentedSecret, storedVerifier)

    override fun hash(secret: String): String = BCrypt.hashpw(secret, BCrypt.gensalt())
}
