package com.profiletailors.smp.shortlinks.domain

import com.profiletailors.common.domain.ValueObject

private const val SHORT_CODE_LENGTH = 10
private val BASE62_PATTERN = Regex("^[0-9A-Za-z]+$")

@JvmInline
@ValueObject
value class ShortCode(val value: String) {
    init {
        require(value.isNotBlank()) { "Short code must not be blank" }
        require(value.length <= MAX_LENGTH) { "Short code exceeds maximum length of $MAX_LENGTH" }
        require(BASE62_PATTERN.matches(value)) { "Short code must contain only Base62 characters" }
    }

    companion object {
        const val DEFAULT_LENGTH = SHORT_CODE_LENGTH
        const val MAX_LENGTH = 32

        val BASE62_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray()
    }
}
