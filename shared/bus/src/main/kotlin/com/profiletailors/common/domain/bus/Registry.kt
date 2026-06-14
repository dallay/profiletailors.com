package com.profiletailors.common.domain.bus

import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandHandler
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.bus.notification.Notification
import com.profiletailors.common.domain.bus.notification.NotificationHandler
import com.profiletailors.common.domain.bus.pipeline.PipelineBehavior
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.common.domain.bus.query.QueryHandler

/**
 * Registry of all handlers and pipeline behaviors used by the [Mediator].
 *
 * Implementations look up handlers by request type at dispatch time.
 * The default implementation is [RegistryImpl].
 *
 * @see Registrar
 */
interface Registry {
    /** Resolve the single handler for a void [Command]. */
    fun <TCommand : Command> resolveCommandHandler(classOfCommand: Class<TCommand>): CommandHandler<TCommand>
    /** Resolve the single handler for a [CommandWithResult]. */
    fun <TCommand : CommandWithResult<TResult>, TResult> resolveCommandWithResultHandler(
        classOfCommand: Class<TCommand>,
    ): CommandWithResultHandler<TCommand, TResult>
    /** Resolve the single handler for a [Query]. */
    fun <TQuery : Query<TResult>, TResult> resolveQueryHandler(
        classOfQuery: Class<TQuery>,
    ): QueryHandler<TQuery, TResult>
    /** Resolve all handlers for a [Notification] (broadcast). */
    fun <TNotification : Notification> resolveNotificationHandlers(
        classOfNotification: Class<TNotification>,
    ): Collection<NotificationHandler<TNotification>>
    /** Return all registered pipeline behaviors in order. */
    fun getPipelineBehaviors(): Collection<PipelineBehavior>
}
