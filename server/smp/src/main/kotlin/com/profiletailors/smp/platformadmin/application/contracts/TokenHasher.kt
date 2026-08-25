package com.profiletailors.smp.platformadmin.application.contracts

/**
 * Port for hashing and verifying invitation tokens.
 *
 * Application layer must NOT depend on Spring Security directly;
 * this port insulates the handler from the underlying hashing framework.
 */
interface TokenHasher {
    fun hash(rawToken: String): String
    fun matches(rawToken: String, storedHash: String): Boolean
}

interface InvitationTokenCandidateKey {
    fun candidateKey(rawToken: String): String
}
