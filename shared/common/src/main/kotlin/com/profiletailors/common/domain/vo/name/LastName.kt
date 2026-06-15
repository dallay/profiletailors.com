package com.profiletailors.common.domain.vo.name

/**
 * A validated last name value object.
 *
 * Ensures the name is non-blank and does not exceed 50 characters.
 *
 * @since 1.0.0
 */
@JvmInline
value class LastName(val value: String) {
    init {
        require(value.isNotBlank()) { "Last name cannot be blank" }
        require(value.length <= MAX_LASTNAME_LENGTH) { "Last name cannot exceed $MAX_LASTNAME_LENGTH characters" }
    }
    override fun toString(): String = value
    companion object {
        private const val MAX_LASTNAME_LENGTH = 50
    }
}
