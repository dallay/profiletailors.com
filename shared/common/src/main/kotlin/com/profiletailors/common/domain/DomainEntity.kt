package com.profiletailors.common.domain

/**
 * Marks a class as an internal entity of a DDD Aggregate.
 *
 * An internal entity has its own identity but lives inside the consistency boundary of an
 * Aggregate Root. It MUST NOT be referenced, constructed, or mutated directly by code outside
 * the aggregate. Every interaction with an internal entity MUST go through its owning
 * Aggregate Root, which guarantees invariants are preserved.
 *
 * The DDD conformance architecture tests (`AggregateBoundaryTest`) enforce this rule by
 * scanning the codebase for cross-package imports of any class carrying this annotation.
 *
 * @see AggregateRoot
 * @since 1.0.0
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class DomainEntity
