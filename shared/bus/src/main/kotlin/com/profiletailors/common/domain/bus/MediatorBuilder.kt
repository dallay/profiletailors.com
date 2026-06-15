package com.profiletailors.common.domain.bus

/**
 * Builder for creating a configured [Mediator] instance.
 *
 * Usage:
 * ```kotlin
 * val mediator = MediatorBuilder(dependencyProvider)
 *     .withPublishStrategy(ParallelNoWaitPublishStrategy())
 *     .build()
 * ```
 *
 * @param dependencyProvider provides handler instances by type
 */
class MediatorBuilder(
    private val dependencyProvider: DependencyProvider,
) {
    internal var defaultPublishStrategy: PublishStrategy = StopOnExceptionPublishStrategy()
        private set

    /** Override the default notification publish strategy. */
    fun withPublishStrategy(publishStrategy: PublishStrategy): MediatorBuilder {
        this.defaultPublishStrategy = publishStrategy
        return this
    }

    /** Build the [Mediator] with the registered handlers and behaviors. */
    fun build(registry: Registry = RegistryImpl(dependencyProvider)): Mediator =
        MediatorImpl(registry, defaultPublishStrategy)
}
