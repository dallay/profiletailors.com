package com.profiletailors.common.domain.bus.command

/**
 * Marker interface for CQRS commands — operations that mutate state.
 *
 * A command represents an intention to change the system state. Commands are named in
 * the imperative tense (e.g., `CreatePost`, `ActivateUser`) and are handled by exactly
 * one [CommandHandler].
 *
 * @see CommandHandler
 * @see com.profiletailors.common.domain.bus.query.Query
 */
interface Command
