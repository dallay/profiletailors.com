package com.profiletailors.leadcapture.common

@JvmInline
value class CaptureLocale(val value: String) {

    init {
        require(value.isNotBlank()) { "Capture locale must not be blank" }
    }

    override fun toString(): String = value
}
