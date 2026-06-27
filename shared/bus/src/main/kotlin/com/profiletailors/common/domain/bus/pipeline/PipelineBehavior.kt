package com.profiletailors.common.domain.bus.pipeline

import com.profiletailors.common.domain.bus.RequestHandlerDelegate

/**
 * Middleware for the mediator pipeline.
 *
 * Pipeline behaviors wrap around command/query handlers to provide cross-cutting
 * concerns such as logging, validation, metrics, and transaction management.
 * Behaviors are executed in registration order, forming a chain around the handler.
 *
 * @see com.profiletailors.common.domain.bus.Mediator
 */
interface PipelineBehavior {
    /**
     * Intercept a request in the mediator pipeline.
     *
     * @param request the command or query being dispatched
     * @param next the next behavior in the chain, or the final handler
     * @return the response from the next element in the pipeline
     */
    suspend fun <TRequest, TResponse> handle(
        request: TRequest,
        next: RequestHandlerDelegate<TRequest, TResponse>,
    ): TResponse
}
