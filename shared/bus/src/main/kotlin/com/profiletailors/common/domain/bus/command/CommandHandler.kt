package com.profiletailors.common.domain.bus.command

/**
 * Handler for a [Command] that mutates state without returning a result.
 *
 * Each command type should have exactly one handler registered in the [Mediator][com.profiletailors.common.domain.bus.Mediator].
 *
 * @param T the command type this handler can process
 */
fun interface CommandHandler<T : Command> {
    /**
     * Execute the command.
     * @param command the command to handle
     */
    suspend fun handle(command: T)
}
