package com.profiletailors.common.domain.security

/**
 * Pluggable hash function for one-way hashing of sensitive values.
 *
 * Implementations are registered by name in [HasherRegistry][com.profiletailors.spring.boot.config.HasherRegistry].
 *
 * @see Sha256Hasher
 * @see HmacHasher
 */
fun interface Hasher {
    /** Hash the input string and return the hex-encoded digest. */
    fun hash(input: String): String
}
