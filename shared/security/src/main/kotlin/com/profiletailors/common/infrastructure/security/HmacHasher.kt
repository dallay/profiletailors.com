package com.profiletailors.common.infrastructure.security

import com.profiletailors.common.domain.security.Hasher
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class HmacHasher(
    private val secretKey: String,
    private val algorithm: String = "HmacSHA256"
) : Hasher {
    override fun hash(input: String): String {
        require(secretKey.isNotBlank()) { "Secret key must not be empty or blank for HMAC" }
        val mac = Mac.getInstance(algorithm)
        val keySpec = SecretKeySpec(secretKey.toByteArray(), algorithm)
        mac.init(keySpec)
        val hmacBytes = mac.doFinal(input.toByteArray())
        return hmacBytes.joinToString("") { "%02x".format(it) }
    }
}
