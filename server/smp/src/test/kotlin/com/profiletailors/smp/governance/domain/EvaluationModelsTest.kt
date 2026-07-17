package com.profiletailors.smp.governance.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class EvaluationModelsTest {

    @Test
    fun `creates evaluation context with null wildcards`() {
        val ctx = ComplianceEvaluationContext(release = "mvp", market = "EEA")
        assertEquals("mvp", ctx.release)
        assertEquals("EEA", ctx.market)
        kotlin.test.assertNull(ctx.environment) // null when not provided
    }

    @Test
    fun `creates evaluation with overall status`() {
        val evaluation = ComplianceEvaluation(
            context = ComplianceEvaluationContext(release = "mvp"),
            overallStatus = EvaluationStatus.COMPLIANT,
            summary = EvaluationSummary(
                totalControls = 5,
                passed = 5,
                failed = 0,
                waived = 0,
                notAssessed = 0,
                warnings = 0,
                notApplicable = 0,
            ),
            controlResults = emptyList(),
            evaluatedAt = java.time.Instant.now(),
            evaluatedBy = "system",
        )

        assertEquals(EvaluationStatus.COMPLIANT, evaluation.overallStatus)
        assertEquals(5, evaluation.summary.totalControls)
    }
}
