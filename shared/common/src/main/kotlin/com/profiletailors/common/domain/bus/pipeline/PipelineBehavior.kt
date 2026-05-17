package com.profiletailors.common.domain.bus.pipeline

import com.profiletailors.common.domain.bus.RequestHandlerDelegate

interface PipelineBehavior {
    suspend fun <TRequest, TResponse> handle(
        request: TRequest,
        next: RequestHandlerDelegate<TRequest, TResponse>
    ): TResponse
}
