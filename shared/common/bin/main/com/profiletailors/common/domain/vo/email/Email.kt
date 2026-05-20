package com.profiletailors.common.domain.vo.email

@JvmInline
value class Email(val value: String) {
    init {
        require(value.isNotBlank()) { "Email cannot be blank" }
        require(value.length <= EMAIL_LEN) { "Email cannot exceed $EMAIL_LEN characters" }
        require(REGEX.matches(value)) { "Email is not valid: $value" }
    }

    companion object {
        private const val EMAIL_LEN = 320

        @Suppress("MaximumLineLength", "MaxLineLength")
        private val REGEX = Regex(
            "^[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+)*@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$",
        )

        fun of(value: String): Email? = try { Email(value) } catch (_: IllegalArgumentException) { null }
    }
}
