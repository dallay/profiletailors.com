package com.profiletailors.common.domain.security

fun interface Hasher {
    fun hash(input: String): String
}
