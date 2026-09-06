package com.profiletailors.smp.privacy.application

/**
 * Convenience function for inline JSON serialization using [PrivacyDataSerializer].
 */
/**
 * Serializes privacy data to a JSON string.
 *
 * @param serializer The serializer used to convert the data.
 * @param data The data to serialize.
 * @return The serialized JSON string.
 */
internal fun mapToJson(serializer: PrivacyDataSerializer, data: Any?): String = serializer.toJson(data)
