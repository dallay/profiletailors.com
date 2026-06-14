package com.profiletailors.common.domain.error

/**
 * Exception that aggregates multiple independent failures into a single throwable.
 *
 * Use this when a batch or multi-step operation encounters multiple errors and you
 * want to report all of them together rather than failing fast on the first one.
 *
 * @param exceptions the collection of individual failures
 * @since 1.0.0
 */
class AggregateException(val exceptions: Collection<Throwable>) : RuntimeException() {
    /**
     * Creates an [AggregateException] from an array of throwables.
     *
     * Convenience overload that converts the array to a list internally, useful when
     * collecting errors incrementally into an array-backed structure.
     *
     * @param exceptions the array of individual failures
     */
    constructor(exceptions: Array<Throwable>) : this(exceptions.toList())
}
