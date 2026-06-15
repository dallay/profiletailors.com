package com.profiletailors.common.infrastructure.security

import com.profiletailors.common.domain.security.Hasher
import java.security.MessageDigest

/**
 * SHA-256 hash implementation.
 *
 * Produces a 64-character hex digest. Suitable for general-purpose hashing
 * where cryptographic verification is not required.
 */
class Sha256Hasher : Hasher {
    override fun hash(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
