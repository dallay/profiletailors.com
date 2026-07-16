package com.profiletailors.leadcapture.common

@JvmInline
value class EmailAddress(val value: String) {

    init {
        require(value.isNotBlank()) { "Email address must not be blank" }
        require(value.length <= 320) { "Email address must be at most 320 characters" }
        val trimmed = value.trim()
        require(trimmed == value) { "Email address must not have leading or trailing whitespace" }
        require(!value.any { it.isWhitespace() }) { "Email address must not contain whitespace" }
        require(value.contains("@")) { "Email address must contain '@'" }
        val parts = value.split("@")
        require(parts.size == 2) { "Email address must contain exactly one '@'" }
        require(parts[0].isNotEmpty()) { "Email address must have a local part before '@'" }
        require(parts[1].isNotEmpty()) { "Email address must have a domain part after '@'" }
        require(parts[1].contains(".")) { "Email address domain must contain a dot" }
    }

    override fun toString(): String = value
}
