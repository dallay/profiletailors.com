package com.profiletailors.smp.governance.domain

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TakedownReportStatusTest {

    @Test
    fun `has all expected status values`() {
        val statuses = TakedownReportStatus.entries.map { it.name }.toSet()

        statuses shouldBe setOf("REPORTED", "APPROVED", "DISMISSED", "SUSPENDED")
    }
}
