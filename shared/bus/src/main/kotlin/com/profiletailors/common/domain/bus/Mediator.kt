package com.profiletailors.common.domain.bus

import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.notification.Notification
import com.profiletailors.common.domain.bus.query.Query

/**
 * Central mediator for dispatching commands, queries, and notifications
 * to their registered handlers.
 *
 * The Mediator decouples the sender from the handler, enabling cross-cutting
 * concerns (logging, validation, metrics) via [PipelineBehavior][com.profiletailors.common.domain.bus.pipeline.PipelineBehavior].
 *
 * @see MediatorBuilder
 */
interface Mediator {
    /** Dispatch a [Query] and return its result. */
    suspend fun <TQuery : Query<TResponse>, TResponse> send(query: TQuery): TResponse
    /** Dispatch a void [Command]. */
    suspend fun <TCommand : Command> send(command: TCommand)
    /** Dispatch a [CommandWithResult] and return its result. */
    suspend fun <TCommand : CommandWithResult<TResult>, TResult> send(command: TCommand): TResult
    /** Publish a [Notification] to all registered handlers using the default strategy. */
    suspend fun <T : Notification> publish(notification: T)
    /** Publish a [Notification] with a specific [PublishStrategy]. */
    suspend fun <T : Notification> publish(notification: T, publishStrategy: PublishStrategy)
}
