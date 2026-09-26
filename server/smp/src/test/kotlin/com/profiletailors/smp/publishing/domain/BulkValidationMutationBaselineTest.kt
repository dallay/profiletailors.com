package com.profiletailors.smp.publishing.domain

import io.github.anschnapp.mutflow.MutFlow
import io.github.anschnapp.mutflow.VerificationMode
import io.github.anschnapp.mutflow.junit.MutFlowTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@MutFlowTest(verificationMode = VerificationMode.LENIENT)
class BulkValidationMutationBaselineTest {
    private val fixedClock = Clock.fixed(Instant.parse("2026-08-30T10:00:00Z"), ZoneOffset.UTC)

    private fun pipeline(): BulkValidationPipeline = BulkValidationPipeline(
        providerCapabilityValidator = ProviderCapabilityValidator { },
        clock = fixedClock,
    )

    @Test
    fun `blank csv returns empty result`() {
        val result =
            MutFlow.underTest {
                runBlocking { pipeline().validate("ws-1", "   ") }
            }
        assertTrue(result.rows.isEmpty())
    }

    @Test
    fun `invalid header returns invalid header row`() {
        val result =
            MutFlow.underTest {
                runBlocking { pipeline().validate("ws-1", "wrong,header\nvalue1,value2") }
            }
        assertEquals(1, result.rows.size)
        assertEquals(BulkRowStatus.INVALID, result.rows.first().status)
        assertTrue(result.rows.first().errors.any { it.code == "INVALID_HEADER" })
    }
}
