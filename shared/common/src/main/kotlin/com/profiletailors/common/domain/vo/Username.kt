package com.profiletailors.common.domain.vo

@JvmInline
value class Username(val value: String) {
    init {
        require(value.isNotBlank()) { "Username cannot be blank" }
        require(value.length in MIN_LENGTH..MAX_LENGTH) {
            "Username must be between $MIN_LENGTH and $MAX_LENGTH characters"
        }
    }
    override fun toString(): String = value
    companion object {
        private const val MAX_LENGTH = 100
        private const val MIN_LENGTH = 3
        fun of(username: String): Username? =
            if (username.isNotBlank() && username.length in MIN_LENGTH..MAX_LENGTH) Username(username) else null
    }
}
