package com.profiletailors.common.domain.vo.name

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
