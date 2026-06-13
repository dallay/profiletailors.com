package com.profiletailors.common.domain.bus.command

/**
 * Handler for a [CommandWithResult] — a command that mutates state and returns a value.
 *
 * @param TCommand the command type this handler can process
 * @param TResult the type of the result returned after execution
 */
fun interface CommandWithResultHandler<TCommand : CommandWithResult<TResult>, TResult> {
    /**
     * Execute the command and return a result.
     * @param command the command to handle
     * @return the execution result
     */
    suspend fun handle(command: TCommand): TResult
}
