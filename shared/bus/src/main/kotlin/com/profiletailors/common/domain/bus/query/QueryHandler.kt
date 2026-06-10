package com.profiletailors.common.domain.bus.query

fun interface QueryHandler<TQuery : Query<TResponse>, TResponse> {
    suspend fun handle(query: TQuery): TResponse
}
