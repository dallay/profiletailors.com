package com.profiletailors.smp.platformadmin.application.ports

/**
 * Port for hashing and verifying invitation tokens.
 *
 * Application layer must NOT depend on Spring Security directly;
 * this port insulates the handler from the underlying hashing framework.
 */
fun interface TokenHasher {
    fun hash(rawToken: String): String
}
