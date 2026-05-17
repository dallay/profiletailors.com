package com.profiletailors.common.domain.bus.command

import com.profiletailors.common.domain.bus.DependencyProvider

internal class CommandProvider<H : CommandHandler<*>>(
    private val dependencyProvider: DependencyProvider,
    private val type: Class<H>,
) {
    fun get(): H = dependencyProvider.getSingleInstanceOf(type)
}

internal class CommandWithResultProvider<H : CommandWithResultHandler<*, *>>(
    private val dependencyProvider: DependencyProvider,
    private val type: Class<H>,
) {
    fun get(): H = dependencyProvider.getSingleInstanceOf(type)
}
