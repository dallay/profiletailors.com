package com.profiletailors.smp.privacy.application

fun interface PrivacyDataSerializer {
    /**
 * Converts data to its JSON representation.
 *
 * @param data The data to serialize, or `null`.
 * @return The JSON representation of the data.
 */
fun toJson(data: Any?): String
}
