package com.profiletailors.smp.privacy.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * JSON utility shared across privacy application services.
 *
 * Uses Jackson with Kotlin module for serialization.
 */
internal object PrivacyJson {
    private val mapper: ObjectMapper = jacksonObjectMapper()

    /**
     * Serializes [data] to a JSON string.
     *
     * Accepts maps, lists, and any Jackson-serializable value.
     */
    fun toJson(data: Any?): String = mapper.writeValueAsString(data)
}

/**
 * Convenience function for inline JSON serialization.
 */
internal fun mapToJson(data: Any?): String = PrivacyJson.toJson(data)
