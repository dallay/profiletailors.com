package com.profiletailors.smp.credentials.application

interface ApiKeySecretVerifier {
    fun matches(presentedSecret: String, storedVerifier: String): Boolean

    fun hash(secret: String): String
}
