package com.profiletailors.common.domain.vo.name

@JvmInline
value class FirstName(val value: String) {
    init {
        require(value.isNotBlank()) { "First name cannot be blank" }
        require(value.length <= MAX_FIRSTNAME_LENGTH) { "First name cannot exceed $MAX_FIRSTNAME_LENGTH characters" }
    }
    override fun toString(): String = value
    companion object {
        private const val MAX_FIRSTNAME_LENGTH = 50
    }
}
