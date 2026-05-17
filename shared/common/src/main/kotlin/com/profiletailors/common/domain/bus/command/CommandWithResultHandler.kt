package com.profiletailors.common.domain.bus.command

interface CommandWithResultHandler<TCommand : CommandWithResult<TResult>, TResult> {
    suspend fun handle(command: TCommand): TResult
}
