package com.profiletailors.smp.credentials.application

interface RefreshTokenHasher {
    fun hash(secret: String): String
    fun matches(presentedSecret: String, storedVerifier: String): Boolean
}
