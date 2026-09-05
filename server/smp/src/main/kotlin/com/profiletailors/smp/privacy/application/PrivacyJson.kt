package com.profiletailors.smp.privacy.application

/**
 * Convenience function for inline JSON serialization using [PrivacyDataSerializer].
 */
internal fun mapToJson(serializer: PrivacyDataSerializer, data: Any?): String = serializer.toJson(data)
