package com.profiletailors.smp.governance.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TakedownReportStatusTest {

    @Test
    fun `has all expected status values`() {
        val statuses = TakedownReportStatus.entries.map { it.name }.toSet()

        assertEquals(setOf("REPORTED", "APPROVED", "DISMISSED", "SUSPENDED"), statuses)
    }
}
