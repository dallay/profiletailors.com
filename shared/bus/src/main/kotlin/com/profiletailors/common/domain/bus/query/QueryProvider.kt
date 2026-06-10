package com.profiletailors.common.domain.bus.query

import com.profiletailors.common.domain.bus.DependencyProvider

internal class QueryProvider<H : QueryHandler<*, *>>(
    private val dependencyProvider: DependencyProvider,
    private val type: Class<H>,
) {
    fun get(): H = dependencyProvider.getSingleInstanceOf(type)
}
