package com.profiletailors.smp.governance.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.bus.PublishStrategy
import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.notification.Notification
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.smp.governance.domain.ComplianceEvaluation
import com.profiletailors.smp.governance.domain.ComplianceEvaluationContext
import com.profiletailors.smp.governance.domain.EvaluationStatus
import com.profiletailors.smp.governance.domain.EvaluationSummary
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class ComplianceControllerTest {

    @Test
    fun `evaluation endpoint dispatches EvaluateComplianceQuery`() = runTest {
        val mediator = CapturingMediator(
            ComplianceEvaluation(
                context = ComplianceEvaluationContext(release = "mvp"),
                overallStatus = EvaluationStatus.COMPLIANT,
                summary = EvaluationSummary(
                    totalControls = 1,
                    passed = 1,
                    failed = 0,
                    waived = 0,
                    notAssessed = 0,
                    warnings = 0,
                    notApplicable = 0,
                ),
                controlResults = emptyList(),
                evaluatedAt = Instant.parse("2026-07-17T12:00:00Z"),
                evaluatedBy = "service-account",
            ),
        )
        val controller = ComplianceController(mediator)

        val response = controller.evaluate(
            request = ComplianceController.EvaluationRequest(
                context = ComplianceController.EvaluationContextRequest(
                    release = "mvp",
                    market = "EEA",
                ),
            ),
        )

        assertEquals(EvaluationStatus.COMPLIANT.name, response.overallStatus)
        assertEquals("mvp", response.context.release)
        assertEquals("EEA", response.context.market)
    }

    @Test
    fun `ping endpoint returns ok`() = runTest {
        val mediator = CapturingMediator(
            ComplianceEvaluation(
                context = ComplianceEvaluationContext(),
                overallStatus = EvaluationStatus.NOT_ASSESSED,
                summary = EvaluationSummary(
                    totalControls = 0,
                    passed = 0,
                    failed = 0,
                    waived = 0,
                    notAssessed = 0,
                    warnings = 0,
                    notApplicable = 0,
                ),
                controlResults = emptyList(),
                evaluatedAt = Instant.parse("2026-07-17T12:00:00Z"),
                evaluatedBy = "system",
            ),
        )
        val controller = ComplianceController(mediator)

        val result = controller.ping()

        assertEquals(mapOf("status" to "ok"), result)
    }

    private class CapturingMediator(private val result: ComplianceEvaluation) : Mediator {
        @Suppress("UNCHECKED_CAST")
        override suspend fun <TQuery : Query<TResponse>, TResponse> send(query: TQuery): TResponse = result as TResponse

        override suspend fun <TCommand : Command> send(command: TCommand) {
            error("Not used")
        }

        override suspend fun <TCommand : CommandWithResult<TResult>, TResult> send(command: TCommand): TResult {
            error("Not used")
        }

        override suspend fun <T : Notification> publish(notification: T) {
            error("Not used")
        }

        override suspend fun <T : Notification> publish(notification: T, publishStrategy: PublishStrategy) {
            error("Not used")
        }
    }
}
