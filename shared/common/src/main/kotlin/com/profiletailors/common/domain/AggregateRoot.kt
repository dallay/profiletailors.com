package com.profiletailors.common.domain

/**
 * Marks a class as a DDD Aggregate Root.
 *
 * The aggregate root is the sole entry point to an aggregate and enforces all invariants across
 * its internal entities and value objects. External code MUST reach the aggregate only through
 * the root; direct access to internal entities (carrying [DomainEntity]) is forbidden.
 *
 * This annotation is the source of truth for the DDD conformance architecture tests
 * (`AggregateBoundaryTest`). Adding it commits the class to the rules defined in
 * ADR-0015 (aggregate root as sole entry point) and ADR-0016 (aggregates communicate by
 * identity only).
 *
 * Note: this is a marker annotation. It does NOT inherit from the `AggregateRoot<ID>` base
 * class in [com.profiletailors.common.domain.model.AggregateRoot], which models identity and
 * domain-event recording for aggregates that need them. Some aggregates are plain `data class`
 * representations and use only the annotation.
 *
 * @see DomainEntity
 * @see com.profiletailors.common.domain.model.AggregateRoot
 * @since 1.0.0
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AggregateRoot
