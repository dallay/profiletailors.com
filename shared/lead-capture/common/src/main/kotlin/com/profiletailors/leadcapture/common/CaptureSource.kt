package com.profiletailors.leadcapture.common

private const val CAPTURE_SOURCE_MAX_LENGTH = 50

@JvmInline
value class CaptureSource(val value: String) {

    init {
        require(value.isNotBlank()) { "Capture source must not be blank" }
        require(value.length <= CAPTURE_SOURCE_MAX_LENGTH) {
            "Capture source must be at most $CAPTURE_SOURCE_MAX_LENGTH characters"
        }
        require(value.all { it.isLetterOrDigit() || it == '-' }) {
            "Capture source must contain only alphanumeric characters and hyphens"
        }
    }

    override fun toString(): String = value
}
