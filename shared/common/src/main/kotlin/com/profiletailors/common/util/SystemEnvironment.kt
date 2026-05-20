package com.profiletailors.common.util

object SystemEnvironment {
    fun getEnvOrDefault(key: String, default: String): String =
        System.getenv(key).takeUnless { it.isNullOrBlank() } ?: default
}
