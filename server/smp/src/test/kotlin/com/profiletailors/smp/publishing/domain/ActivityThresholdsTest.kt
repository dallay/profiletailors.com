package com.profiletailors.smp.publishing.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ActivityThresholdsTest {

    @ParameterizedTest
    @CsvSource(
        "-1, NONE",
        "0, NONE",
        "1, LIGHT",
        "2, LIGHT",
        "3, MEDIUM",
        "5, MEDIUM",
        "6, HIGH",
        "10, HIGH",
        "100, HIGH",
    )
    fun `classifies count into correct density level`(count: Int, expected: String) {
        assertEquals(ActivityDensity.valueOf(expected), ActivityThresholds.classify(count))
    }
}
