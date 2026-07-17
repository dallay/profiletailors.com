package com.profiletailors.leadcapture.common

@JvmInline
value class NormalizedEmail private constructor(val value: String) {

    override fun toString(): String = value

    companion object {
        fun from(email: EmailAddress): NormalizedEmail = NormalizedEmail(email.value.trim().lowercase())

        fun fromPersisted(value: String): NormalizedEmail = NormalizedEmail(value)
    }
}
