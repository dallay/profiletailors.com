package com.profiletailors.common.domain.bus.command

class CommandHandlerExecutionError(
    message: String? = null,
    cause: Throwable? = null
) : RuntimeException(message, cause)
