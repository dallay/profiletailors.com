package com.profiletailors.smp.mcp.application

interface McpJsonSerializer {
    /**
 * Serializes a value into a JSON string.
 *
 * @param data The value to serialize.
 * @return The JSON representation of the value.
 */
fun <T> toJson(data: T): String
    /**
 * Deserializes a JSON string into an instance of the specified type.
 *
 * @param json The JSON string to deserialize.
 * @param type The class of the resulting instance.
 * @return The deserialized instance.
 */
fun <T> fromJson(json: String, type: Class<T>): T
}
