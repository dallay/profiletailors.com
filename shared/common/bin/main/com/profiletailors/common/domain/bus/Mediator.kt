package com.profiletailors.common.domain.bus

import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.notification.Notification
import com.profiletailors.common.domain.bus.query.Query

interface Mediator {
    suspend fun <TQuery : Query<TResponse>, TResponse> send(query: TQuery): TResponse
    suspend fun <TCommand : Command> send(command: TCommand)
    suspend fun <TCommand : CommandWithResult<TResult>, TResult> send(command: TCommand): TResult
    suspend fun <T : Notification> publish(notification: T)
    suspend fun <T : Notification> publish(notification: T, publishStrategy: PublishStrategy)
}
