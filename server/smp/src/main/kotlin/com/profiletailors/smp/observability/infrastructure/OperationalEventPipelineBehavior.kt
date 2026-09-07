package com.profiletailors.smp.observability.infrastructure

import com.profiletailors.common.domain.bus.RequestHandlerDelegate
import com.profiletailors.common.domain.bus.pipeline.PipelineBehavior
import com.profiletailors.observability.OperationalEvent
import com.profiletailors.observability.OperationalEventSink
import com.profiletailors.observability.Severity
import org.springframework.stereotype.Component

@Component
class OperationalEventPipelineBehavior(private val operationalEvents: OperationalEventSink) : PipelineBehavior {
    override suspend fun <TRequest, TResponse> handle(
        request: TRequest,
        next: RequestHandlerDelegate<TRequest, TResponse>,
    ): TResponse {
        val requestName = request?.let { it::class.simpleName } ?: "anonymous"
        val startedAt = System.nanoTime()
        operationalEvents.emit(
            OperationalEvent(
                name = "bus.request.started",
                severity = Severity.INFO,
                attributes = mapOf("request" to requestName),
            ),
        )
        val result = runCatching { next(request) }
        result.onSuccess {
            operationalEvents.emit(
                OperationalEvent(
                    name = "bus.request.completed",
                    severity = Severity.INFO,
                    attributes = mapOf(
                        "request" to requestName,
                        "durationMs" to elapsedMillis(startedAt),
                    ),
                ),
            )
        }
        result.onFailure { failure -> emitFailure(requestName, startedAt, failure) }
        return result.getOrThrow()
    }

    private fun emitFailure(requestName: String, startedAt: Long, failure: Throwable) {
        operationalEvents.emit(
            OperationalEvent(
                name = "bus.request.failed",
                severity = Severity.ERROR,
                attributes = mapOf(
                    "request" to requestName,
                    "durationMs" to elapsedMillis(startedAt),
                ),
                cause = failure,
            ),
        )
    }

    private fun elapsedMillis(startedAt: Long): Long = (System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
