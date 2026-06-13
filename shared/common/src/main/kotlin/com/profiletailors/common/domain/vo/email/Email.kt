package com.profiletailors.common.domain.vo.email

/**
 * A validated email address value object.
 *
 * Ensures the email conforms to a practical subset of RFC 5321:
 * - Must not be blank
 * - Maximum 320 characters
 * - Must match a regex that validates local-part (dots allowed but not leading/trailing/consecutive)
 *   and domain part structure
 *
 * ## Factory method
 * [of] provides a safe factory that returns `null` for invalid input instead of throwing,
 * making it suitable for optional or user-facing email fields where validation is lenient.
 *
 * @throws IllegalArgumentException if the value is blank, too long, or does not match the email pattern
 * @since 1.0.0
 */
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

        /**
         * Safely creates an [Email], returning `null` for invalid input.
         *
         * @param value the raw email string
         * @return a validated [Email], or `null` if validation fails
         */
        fun of(value: String): Email? = try { Email(value) } catch (_: IllegalArgumentException) { null }
    }
}
