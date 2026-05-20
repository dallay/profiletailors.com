package com.profiletailors.common.domain.bus.pipeline

import com.profiletailors.common.domain.bus.DependencyProvider

internal class PipelineProvider<H : PipelineBehavior>(
    private val dependencyProvider: DependencyProvider,
    private val type: Class<H>,
) {
    fun get(): H = dependencyProvider.getSingleInstanceOf(type)
}
