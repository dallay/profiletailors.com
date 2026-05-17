package com.profiletailors.common.domain.bus.command

fun interface CommandHandler<T : Command> {
    suspend fun handle(command: T)
}
