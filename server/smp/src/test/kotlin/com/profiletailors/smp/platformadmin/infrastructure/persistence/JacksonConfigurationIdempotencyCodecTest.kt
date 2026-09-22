package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.profiletailors.smp.governance.application.AdminTakedownReport
import com.profiletailors.smp.platformadmin.infrastructure.http.RegistrationModeResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class JacksonConfigurationIdempotencyCodecTest {

    private val codec = JacksonConfigurationIdempotencyCodec()

    @Test
    fun `should round-trip registration mode results through json`() {
        val result = RegistrationModeResult("INVITE_ONLY")

        val decoded = codec.decode(codec.encode(result), RegistrationModeResult::class.java)

        assertEquals(result, decoded)
    }

    @Test
    fun `should round-trip takedown reports holding instants through json`() {
        val now = Instant.parse("2026-09-22T10:00:00Z")
        val result = AdminTakedownReport(
            reportId = "report-1",
            workspaceId = "ws-1",
            assetId = "asset-1",
            reportedById = "user-1",
            reason = "copyright",
            status = "APPROVED",
            rejectionReason = null,
            reviewedById = "owner-1",
            reviewedAt = now,
            reporterEmail = "reporter@example.com",
            mediaReferenceUrl = null,
            createdAt = now,
            updatedAt = now,
        )

        val decoded = codec.decode(codec.encode(result), AdminTakedownReport::class.java)

        assertEquals(result, decoded)
    }
}
