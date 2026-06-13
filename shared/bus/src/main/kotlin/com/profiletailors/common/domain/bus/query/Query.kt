package com.profiletailors.common.domain.bus.query

/**
 * Marker interface for CQRS queries — operations that read state without side effects.
 *
 * A query represents a request for data. Queries are named in the interrogative tense
 * (e.g., `GetPost`, `ListUsers`) and are handled by exactly one [QueryHandler].
 *
 * @param TResponse the type of data returned by this query
 * @see QueryHandler
 * @see com.profiletailors.common.domain.bus.command.Command
 */
interface Query<TResponse>
