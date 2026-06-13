package com.profiletailors.common.domain.bus.command

/**
 * Marker interface for CQRS commands that return a result after execution.
 *
 * Use when the caller needs feedback from the command (e.g., the created entity's ID).
 * For commands that mutate state without a return value, use [Command] instead.
 *
 * @param TResult the type of the result returned after command execution
 * @see Command
 * @see CommandWithResultHandler
 */
interface CommandWithResult<TResult>
