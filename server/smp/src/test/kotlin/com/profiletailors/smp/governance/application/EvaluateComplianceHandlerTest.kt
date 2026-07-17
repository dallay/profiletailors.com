package com.profiletailors.smp.governance.application

import com.profiletailors.smp.governance.domain.ComplianceControl
import com.profiletailors.smp.governance.domain.ComplianceControlId
import com.profiletailors.smp.governance.domain.ComplianceControlRepository
import com.profiletailors.smp.governance.domain.ComplianceEvaluationContext
import com.profiletailors.smp.governance.domain.ComplianceEvidenceRepository
import com.profiletailors.smp.governance.domain.ComplianceRiskAcceptanceRepository
import com.profiletailors.smp.governance.domain.ControlStatus
import com.profiletailors.smp.governance.domain.EvaluationStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals

internal class EvaluateComplianceHandlerTest {

    private val controlRepo: ComplianceControlRepository = mockk()
    private val evidenceRepo: ComplianceEvidenceRepository = mockk()
    private val riskAcceptanceRepo: ComplianceRiskAcceptanceRepository = mockk()
    private val fixedClock: Clock = Clock.fixed(
        Instant.parse("2026-07-17T12:00:00Z"),
        ZoneId.of("UTC"),
    )

    private val handler = EvaluateComplianceHandler(
        controlRepository = controlRepo,
        evidenceRepository = evidenceRepo,
        riskAcceptanceRepository = riskAcceptanceRepo,
        clock = fixedClock,
    )

    @Test
    fun `returns NOT_ASSESSED when no applicable controls`() = runTest {
        val ctx = ComplianceEvaluationContext(release = "mvp", market = "EEA")

        every { controlRepo.findApplicable(ctx, fixedClock.instant()) } returns emptyFlow()
        every { evidenceRepo.findByControlId(any()) } returns emptyFlow()
        every { riskAcceptanceRepo.findActiveForControl(any(), any(), any()) } returns emptyFlow()

        val result = handler.handle(EvaluateComplianceQuery(context = ctx))

        assertEquals(EvaluationStatus.NOT_ASSESSED, result.overallStatus)
        assertEquals(0, result.summary.totalControls)
    }

    @Test
    fun `returns COMPLIANT when all required controls pass`() = runTest {
        val ctx = ComplianceEvaluationContext(release = "mvp")
        val control = ComplianceControl(
            id = ComplianceControlId("ctrl-001"),
            controlKey = "PRIVACY.DATA_RETENTION",
            name = "Data retention",
            category = "PRIVACY",
        )

        every { controlRepo.findApplicable(ctx, fixedClock.instant()) } returns flowOf(
            mockApplicableControl(control, required = true),
        )
        every { evidenceRepo.findByControlId(control.id) } returns flowOf(mockk())
        every { riskAcceptanceRepo.findActiveForControl(any(), any(), any()) } returns emptyFlow()

        val result = handler.handle(EvaluateComplianceQuery(context = ctx))

        assertEquals(EvaluationStatus.COMPLIANT, result.overallStatus)
        assertEquals(1, result.summary.passed)
        assertEquals(ControlStatus.PASS, result.controlResults.first().status)
    }

    @Test
    fun `returns NON_COMPLIANT when a required control has no evidence`() = runTest {
        val ctx = ComplianceEvaluationContext(release = "mvp")
        val control = ComplianceControl(
            id = ComplianceControlId("ctrl-002"),
            controlKey = "PRIVACY.CONSENT",
            name = "Valid consent",
            category = "PRIVACY",
        )

        every { controlRepo.findApplicable(ctx, fixedClock.instant()) } returns flowOf(
            mockApplicableControl(control, required = true),
        )
        every { evidenceRepo.findByControlId(control.id) } returns emptyFlow()
        every { riskAcceptanceRepo.findActiveForControl(any(), any(), any()) } returns emptyFlow()

        val result = handler.handle(EvaluateComplianceQuery(context = ctx))

        assertEquals(EvaluationStatus.NON_COMPLIANT, result.overallStatus)
        assertEquals(1, result.summary.failed)
        assertEquals(ControlStatus.FAIL, result.controlResults.first().status)
    }

    @Test
    fun `returns PARTIAL when a failing control has a waiver`() = runTest {
        val ctx = ComplianceEvaluationContext(release = "mvp")
        val control = ComplianceControl(
            id = ComplianceControlId("ctrl-003"),
            controlKey = "DATA.SUBJECT_RIGHTS",
            name = "Data subject rights",
            category = "PRIVACY",
        )
        val acceptance = mockk<com.profiletailors.smp.governance.domain.ComplianceRiskAcceptance>()

        every { controlRepo.findApplicable(ctx, fixedClock.instant()) } returns flowOf(
            mockApplicableControl(control, required = true),
        )
        every { evidenceRepo.findByControlId(control.id) } returns emptyFlow()
        every { riskAcceptanceRepo.findActiveForControl(any(), any(), any()) } returns flowOf(acceptance)
        every { acceptance.status } returns com.profiletailors.smp.governance.domain.RiskAcceptanceStatus.ACTIVE
        every { acceptance.releaseScope } returns null
        every { acceptance.marketScope } returns null
        every { acceptance.environmentScope } returns null
        every { acceptance.providerScope } returns null
        every { acceptance.productScope } returns null
        every { acceptance.workspaceScope } returns null

        val result = handler.handle(EvaluateComplianceQuery(context = ctx))

        assertEquals(EvaluationStatus.PARTIAL, result.overallStatus)
        assertEquals(1, result.summary.waived)
        assertEquals(ControlStatus.WAIVED, result.controlResults.first().status)
    }

    private fun mockApplicableControl(control: ComplianceControl, required: Boolean = true) =
        com.profiletailors.smp.governance.domain.ApplicableComplianceControl(
            control = control,
            matchingRule = com.profiletailors.smp.governance.domain.ComplianceControlApplicabilityRule(
                id = com.profiletailors.smp.governance.domain.ComplianceControlApplicabilityRuleId("rule-001"),
                controlId = control.id,
                required = required,
            ),
            required = required,
        )
}
