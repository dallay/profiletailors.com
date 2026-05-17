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

interface Registry {
    fun <TCommand : Command> resolveCommandHandler(classOfCommand: Class<TCommand>): CommandHandler<TCommand>
    fun <TCommand : CommandWithResult<TResult>, TResult> resolveCommandWithResultHandler(
        classOfCommand: Class<TCommand>,
    ): CommandWithResultHandler<TCommand, TResult>
    fun <TQuery : Query<TResult>, TResult> resolveQueryHandler(
        classOfQuery: Class<TQuery>,
    ): QueryHandler<TQuery, TResult>
    fun <TNotification : Notification> resolveNotificationHandlers(
        classOfNotification: Class<TNotification>,
    ): Collection<NotificationHandler<TNotification>>
    fun getPipelineBehaviors(): Collection<PipelineBehavior>
}
