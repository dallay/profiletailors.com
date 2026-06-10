package com.profiletailors.common.domain.bus

class MediatorBuilder(
    private val dependencyProvider: DependencyProvider,
) {
    internal var defaultPublishStrategy: PublishStrategy = StopOnExceptionPublishStrategy()
        private set

    fun withPublishStrategy(publishStrategy: PublishStrategy): MediatorBuilder {
        this.defaultPublishStrategy = publishStrategy
        return this
    }

    fun build(registry: Registry = RegistryImpl(dependencyProvider)): Mediator =
        MediatorImpl(registry, defaultPublishStrategy)
}
