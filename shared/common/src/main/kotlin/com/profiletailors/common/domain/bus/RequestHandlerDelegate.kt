package com.profiletailors.common.domain.bus

typealias RequestHandlerDelegate<TRequest, TResponse> = suspend (TRequest) -> TResponse
