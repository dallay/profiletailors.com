package com.profiletailors.common.domain.bus.query

/**
 * Handler for a [Query] — a read operation that returns data.
 *
 * Each query type should have exactly one handler registered in the [Mediator][com.profiletailors.common.domain.bus.Mediator].
 *
 * @param TQuery the query type this handler can process
 * @param TResponse the type of data returned by the query
 */
fun interface QueryHandler<TQuery : Query<TResponse>, TResponse> {
    /**
     * Execute the query and return the result.
     * @param query the query to handle
     * @return the queried data
     */
    suspend fun handle(query: TQuery): TResponse
}
