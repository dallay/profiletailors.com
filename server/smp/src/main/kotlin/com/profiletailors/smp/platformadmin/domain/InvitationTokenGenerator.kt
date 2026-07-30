package com.profiletailors.smp.platformadmin.domain

import java.security.SecureRandom
import java.util.Base64

object InvitationTokenGenerator {
    private val secureRandom = SecureRandom.getInstanceStrong()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    fun generate(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return encoder.encodeToString(bytes)
    }
}
