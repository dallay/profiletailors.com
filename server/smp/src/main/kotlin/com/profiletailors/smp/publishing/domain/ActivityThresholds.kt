package com.profiletailors.smp.publishing.domain

import com.profiletailors.common.domain.ValueObject

@ValueObject
enum class ActivityDensity {
    NONE,
    LIGHT,
    MEDIUM,
    HIGH,
}

/**
 * Constants and classification for per-day publication activity density.
 *
 * | Count      | Density | Display        |
 * |------------|---------|----------------|
 * | 0          | none    | No dot         |
 * | 1–2        | light   | Small dot      |
 * | 3–5        | medium  | Medium dot     |
 * | 6+         | high    | Large dot + "+"|
 */
object ActivityThresholds {
    const val LIGHT_MAX = 2
    const val MEDIUM_MAX = 5

    fun classify(count: Int): ActivityDensity = when {
        count <= 0 -> ActivityDensity.NONE
        count <= LIGHT_MAX -> ActivityDensity.LIGHT
        count <= MEDIUM_MAX -> ActivityDensity.MEDIUM
        else -> ActivityDensity.HIGH
    }
}
