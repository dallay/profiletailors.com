package com.profiletailors.smp.platformadmin.domain

import java.security.SecureRandom
import java.util.Base64

object InvitationTokenGenerator {
    private val secureRandom = SecureRandom.getInstanceStrong()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    // 32 bytes = 256 bits of entropy, URL-safe base64-encoded
    fun generate(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return encoder.encodeToString(bytes)
    }
}
