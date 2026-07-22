package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.governance.domain.ComplianceEvaluation
import com.profiletailors.smp.governance.domain.ComplianceEvaluationContext
import com.profiletailors.smp.governance.domain.EvaluationStatus
import com.profiletailors.smp.governance.domain.EvaluationSummary
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

internal class ReleaseGateHandlerTest {

    private val mediator: Mediator = mockk()
    private val handler = ReleaseGateHandler(mediator)

    @Test
    fun `returns NOT_APPLICABLE when there are no applicable controls`() = runTest {
        coEvery { mediator.send(any<EvaluateComplianceQuery>()) } returns evaluation(
            totalControls = 0,
            passed = 0,
            failed = 0,
            waived = 0,
        )

        val result = handler.handle(ReleaseGateQuery(release = "0.1.0"))

        result.gateStatus shouldBe "NOT_APPLICABLE"
        result.release shouldBe "0.1.0"
        result.totalControls shouldBe 0
    }

    @Test
    fun `returns FAIL when at least one control fails`() = runTest {
        coEvery { mediator.send(any<EvaluateComplianceQuery>()) } returns evaluation(
            totalControls = 3,
            passed = 1,
            failed = 1,
            waived = 1,
        )

        val result = handler.handle(ReleaseGateQuery(release = "1.2.0"))

        result.gateStatus shouldBe "FAIL"
        result.failed shouldBe 1
    }

    @Test
    fun `returns FAIL when a control fails even if all other controls pass`() = runTest {
        coEvery { mediator.send(any<EvaluateComplianceQuery>()) } returns evaluation(
            totalControls = 4,
            passed = 3,
            failed = 1,
            waived = 0,
        )

        val result = handler.handle(ReleaseGateQuery(release = "1.2.0"))

        result.gateStatus shouldBe "FAIL"
    }

    @Test
    fun `returns PASS when every control passed`() = runTest {
        coEvery { mediator.send(any<EvaluateComplianceQuery>()) } returns evaluation(
            totalControls = 2,
            passed = 2,
            failed = 0,
            waived = 0,
        )

        val result = handler.handle(ReleaseGateQuery(release = "1.2.0"))

        result.gateStatus shouldBe "PASS"
    }

    @Test
    fun `returns PASS when controls are a mix of passed and waived`() = runTest {
        coEvery { mediator.send(any<EvaluateComplianceQuery>()) } returns evaluation(
            totalControls = 3,
            passed = 1,
            failed = 0,
            waived = 2,
        )

        val result = handler.handle(ReleaseGateQuery(release = "1.2.0"))

        result.gateStatus shouldBe "PASS"
    }

    @Test
    fun `returns NOT_APPLICABLE when controls exist but are neither passed, waived, nor failed`() = runTest {
        coEvery { mediator.send(any<EvaluateComplianceQuery>()) } returns evaluation(
            totalControls = 3,
            passed = 1,
            failed = 0,
            waived = 0,
        )

        val result = handler.handle(ReleaseGateQuery(release = "1.2.0"))

        result.gateStatus shouldBe "NOT_APPLICABLE"
    }

    @Test
    fun `dispatches EvaluateComplianceQuery scoped to the requested release`() = runTest {
        val querySlot = slot<EvaluateComplianceQuery>()
        coEvery { mediator.send(capture(querySlot)) } returns evaluation(
            totalControls = 0,
            passed = 0,
            failed = 0,
            waived = 0,
        )

        handler.handle(ReleaseGateQuery(release = "2.0.0"))

        querySlot.captured.context shouldBe ComplianceEvaluationContext(release = "2.0.0")
    }

    @Test
    fun `maps evaluation summary and evaluatedAt into the release gate result`() = runTest {
        val evaluatedAt = Instant.parse("2026-07-22T12:00:00Z")
        coEvery { mediator.send(any<EvaluateComplianceQuery>()) } returns evaluation(
            totalControls = 4,
            passed = 2,
            failed = 1,
            waived = 1,
            evaluatedAt = evaluatedAt,
        )

        val result = handler.handle(ReleaseGateQuery(release = "3.0.0"))

        result.totalControls shouldBe 4
        result.passed shouldBe 2
        result.failed shouldBe 1
        result.waived shouldBe 1
        result.evaluatedAt shouldBe evaluatedAt.toString()
    }

    private fun evaluation(
        totalControls: Int,
        passed: Int,
        failed: Int,
        waived: Int,
        evaluatedAt: Instant = Instant.parse("2026-07-22T12:00:00Z"),
    ): ComplianceEvaluation = ComplianceEvaluation(
        context = ComplianceEvaluationContext(),
        overallStatus = EvaluationStatus.NOT_ASSESSED,
        summary = EvaluationSummary(
            totalControls = totalControls,
            passed = passed,
            failed = failed,
            waived = waived,
            notAssessed = totalControls - passed - failed - waived,
            warnings = 0,
            notApplicable = 0,
        ),
        controlResults = emptyList(),
        evaluatedAt = evaluatedAt,
        evaluatedBy = "system",
    )
}