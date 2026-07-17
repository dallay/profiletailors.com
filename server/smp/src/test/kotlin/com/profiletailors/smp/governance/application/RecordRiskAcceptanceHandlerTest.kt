package com.profiletailors.smp.governance.application

import com.profiletailors.smp.governance.domain.ComplianceControlId
import com.profiletailors.smp.governance.domain.ComplianceRiskAcceptance
import com.profiletailors.smp.governance.domain.ComplianceRiskAcceptanceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class RecordRiskAcceptanceHandlerTest {

    private val repository: ComplianceRiskAcceptanceRepository = mockk()
    private val handler = RecordRiskAcceptanceHandler(repository)

    @Test
    fun `creates risk acceptance with generated id and saves`() = runTest {
        val expiresAt = Instant.parse("2027-06-01T00:00:00Z")
        val command = RecordRiskAcceptanceCommand(
            controlId = ComplianceControlId("ctrl-001"),
            releaseScope = "mvp",
            marketScope = "EEA",
            environmentScope = "production",
            providerScope = "aws",
            productScope = "platform",
            workspaceScope = "acme-corp",
            riskSummary = "Low residual risk accepted for MVP launch",
            residualRisk = "low",
            justification = "Risk is within tolerance",
            requestedBy = "security-lead",
            expiresAt = expiresAt,
        )

        val savedSlot = slot<ComplianceRiskAcceptance>()
        coEvery { repository.save(capture(savedSlot)) } answers { firstArg() }

        handler.handle(command)

        coVerify(exactly = 1) { repository.save(any()) }
        val saved = savedSlot.captured
        assertTrue(saved.id.value.startsWith("ra-"))
        assertEquals(command.controlId, saved.controlId)
        assertEquals(command.releaseScope, saved.releaseScope)
        assertEquals(command.marketScope, saved.marketScope)
        assertEquals(command.environmentScope, saved.environmentScope)
        assertEquals(command.providerScope, saved.providerScope)
        assertEquals(command.productScope, saved.productScope)
        assertEquals(command.workspaceScope, saved.workspaceScope)
        assertEquals(command.riskSummary, saved.riskSummary)
        assertEquals(command.residualRisk, saved.residualRisk)
        assertEquals(command.justification, saved.justification)
        assertEquals(command.requestedBy, saved.requestedBy)
        assertEquals(command.expiresAt, saved.expiresAt)
    }

    @Test
    fun `creates risk acceptance with minimal fields`() = runTest {
        val expiresAt = Instant.parse("2027-06-01T00:00:00Z")
        val command = RecordRiskAcceptanceCommand(
            controlId = ComplianceControlId("ctrl-002"),
            riskSummary = "Accepted",
            requestedBy = "system",
            expiresAt = expiresAt,
        )

        val savedSlot = slot<ComplianceRiskAcceptance>()
        coEvery { repository.save(capture(savedSlot)) } answers { firstArg() }

        handler.handle(command)

        coVerify(exactly = 1) { repository.save(any()) }
        val saved = savedSlot.captured
        assertTrue(saved.id.value.startsWith("ra-"))
        assertEquals(command.controlId, saved.controlId)
        assertEquals(command.riskSummary, saved.riskSummary)
        assertEquals(command.requestedBy, saved.requestedBy)
        assertEquals(command.expiresAt, saved.expiresAt)
        assertNull(saved.releaseScope)
        assertNull(saved.residualRisk)
        assertNull(saved.justification)
    }
}
