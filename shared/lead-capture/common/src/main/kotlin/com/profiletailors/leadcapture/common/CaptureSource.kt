package com.profiletailors.leadcapture.common

@JvmInline
value class CaptureSource(val value: String) {

    init {
        require(value.isNotBlank()) { "Capture source must not be blank" }
    }

    override fun toString(): String = value
}
