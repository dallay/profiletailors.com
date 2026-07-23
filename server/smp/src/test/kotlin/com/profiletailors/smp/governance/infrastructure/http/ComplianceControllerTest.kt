package com.profiletailors.smp.governance.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.bus.PublishStrategy
import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.notification.Notification
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.smp.governance.application.ReleaseGateQuery
import com.profiletailors.smp.governance.application.ReleaseGateResult
import com.profiletailors.smp.governance.domain.ComplianceEvaluation
import com.profiletailors.smp.governance.domain.ComplianceEvaluationContext
import com.profiletailors.smp.governance.domain.EvaluationStatus
import com.profiletailors.smp.governance.domain.EvaluationSummary
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
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

        response.overallStatus shouldBe EvaluationStatus.COMPLIANT.name
        response.context.release shouldBe "mvp"
        response.context.market shouldBe "EEA"
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

        result shouldBe mapOf("status" to "ok")
    }

    @Test
    fun `release-gate returns PASS when all controls pass`() = runTest {
        val mediator = ReleaseGateMediator(
            "0.1.0",
            ReleaseGateResult(
                release = "0.1.0",
                gateStatus = "PASS",
                totalControls = 2,
                passed = 2,
                failed = 0,
                waived = 0,
                evaluatedAt = "2026-07-22T12:00:00Z",
            ),
        )
        val controller = ComplianceController(mediator)

        val result = controller.releaseGate(release = "0.1.0")

        result.gateStatus shouldBe "PASS"
        result.release shouldBe "0.1.0"
        result.totalControls shouldBe 2
        result.passed shouldBe 2
    }

    @Test
    fun `release-gate returns NOT_APPLICABLE with default release parameter`() = runTest {
        val mediator = ReleaseGateMediator(
            "0.1.0",
            ReleaseGateResult(
                release = "0.1.0",
                gateStatus = "NOT_APPLICABLE",
                totalControls = 0,
                passed = 0,
                failed = 0,
                waived = 0,
                evaluatedAt = "2026-07-22T12:00:00Z",
            ),
        )
        val controller = ComplianceController(mediator)

        val result = controller.releaseGate()

        result.gateStatus shouldBe "NOT_APPLICABLE"
        result.release shouldBe "0.1.0"
        result.totalControls shouldBe 0
    }

    @Test
    fun `release-gate returns FAIL when any control fails`() = runTest {
        val mediator = ReleaseGateMediator(
            "0.1.0",
            ReleaseGateResult(
                release = "0.1.0",
                gateStatus = "FAIL",
                totalControls = 2,
                passed = 1,
                failed = 1,
                waived = 0,
                evaluatedAt = "2026-07-22T12:00:00Z",
            ),
        )
        val controller = ComplianceController(mediator)

        val result = controller.releaseGate(release = "0.1.0")

        result.gateStatus shouldBe "FAIL"
        result.failed shouldBe 1
        result.passed shouldBe 1
    }

    @Test
    fun `release-gate returns NOT_APPLICABLE when there are no applicable controls`() = runTest {
        val mediator = ReleaseGateMediator(
            "0.1.0",
            ReleaseGateResult(
                release = "0.1.0",
                gateStatus = "NOT_APPLICABLE",
                totalControls = 0,
                passed = 0,
                failed = 0,
                waived = 0,
                evaluatedAt = "2026-07-22T12:00:00Z",
            ),
        )
        val controller = ComplianceController(mediator)

        val result = controller.releaseGate(release = "0.1.0")

        result.gateStatus shouldBe "NOT_APPLICABLE"
        result.totalControls shouldBe 0
    }

    @Test
    fun `release-gate uses release 0-1-0 as the default when no argument is supplied`() = runTest {
        val mediator = ReleaseGateMediator(
            "0.1.0",
            ReleaseGateResult(
                release = "0.1.0",
                gateStatus = "NOT_APPLICABLE",
                totalControls = 0,
                passed = 0,
                failed = 0,
                waived = 0,
                evaluatedAt = "2026-07-22T12:00:00Z",
            ),
        )
        val controller = ComplianceController(mediator)

        val result = controller.releaseGate()

        result.release shouldBe "0.1.0"
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

    private class ReleaseGateMediator(private val expectedRelease: String, private val result: ReleaseGateResult) :
        Mediator {
        @Suppress("UNCHECKED_CAST")
        override suspend fun <TQuery : Query<TResponse>, TResponse> send(query: TQuery): TResponse {
            if (query is ReleaseGateQuery) {
                query.release shouldBe expectedRelease
                return result as TResponse
            }
            error("Unexpected query type: ${query::class.simpleName}")
        }

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
