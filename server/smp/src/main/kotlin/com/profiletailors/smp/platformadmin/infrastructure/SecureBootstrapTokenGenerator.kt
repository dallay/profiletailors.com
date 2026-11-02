package com.profiletailors.smp.platformadmin.infrastructure

import com.profiletailors.smp.platformadmin.domain.BootstrapToken
import com.profiletailors.smp.platformadmin.domain.BootstrapTokenDigest
import com.profiletailors.smp.platformadmin.domain.BootstrapTokenGenerator
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64

@Component
class SecureBootstrapTokenGenerator : BootstrapTokenGenerator {
    private val secureRandom = SecureRandom()

    override fun generate(): BootstrapToken {
        val bytes = ByteArray(TOKEN_BYTE_SIZE)
        secureRandom.nextBytes(bytes)
        val rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val tokenHash = BootstrapTokenDigest.hash(rawToken)
        return BootstrapToken(rawToken, tokenHash)
    }

    private companion object {
        private const val TOKEN_BYTE_SIZE = 32
    }
}
