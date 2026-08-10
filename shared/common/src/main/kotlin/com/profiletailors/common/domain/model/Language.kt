package com.profiletailors.common.domain.model

import com.profiletailors.common.domain.ValueObject

@ValueObject
enum class Language(val code: String) {
    ENGLISH("en"),
    SPANISH("es"),
    ;

    companion object {
        fun fromString(code: String?): Language = entries.find { it.code.equals(code, ignoreCase = true) }
            ?: throw IllegalArgumentException(
                "Invalid language code '$code'. Supported codes: ${entries.joinToString { it.code }}",
            )
    }
}
