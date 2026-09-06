package com.profiletailors.smp.mcp.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.profiletailors.common.domain.Service
import com.profiletailors.smp.mcp.application.McpJsonSerializer

@Service
class JacksonMcpJsonSerializer(private val objectMapper: ObjectMapper = defaultObjectMapper()) : McpJsonSerializer {

    /**
 * Serializes a value to a JSON string.
 *
 * @param data The value to serialize.
 * @return The JSON representation of the value.
 */
override fun <T> toJson(data: T): String = objectMapper.writeValueAsString(data)

    /**
 * Deserializes a JSON string into an instance of the specified type.
 *
 * @param json The JSON string to deserialize.
 * @param type The class of the target type.
 * @return The deserialized value.
 */
override fun <T> fromJson(json: String, type: Class<T>): T = objectMapper.readValue(json, type)

    companion object {
        /**
             * Creates an ObjectMapper configured for Kotlin and Java time types.
             *
             * @return An ObjectMapper that serializes Java time values as ISO-formatted strings.
             */
            fun defaultObjectMapper(): ObjectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }
}
