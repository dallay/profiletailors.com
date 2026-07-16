package com.profiletailors.leadcapture.common

@JvmInline
value class CaptureSource(val value: String) {

    init {
        require(value.isNotBlank()) { "Capture source must not be blank" }
        require(value.length <= 50) { "Capture source must be at most 50 characters" }
        require(value.all { it.isLetterOrDigit() || it == '-' }) { "Capture source must contain only alphanumeric characters and hyphens" }
    }

    override fun toString(): String = value
}
