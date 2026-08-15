package com.profiletailors.common.domain

/**
 * Marks a class as a DDD Value Object.
 *
 * A value object is an immutable, side-effect-free descriptor defined entirely by its
 * attributes. Two value objects with the same attributes are considered equal. Value objects
 * validate their invariants at construction time (init block, factory, or `require` checks)
 * and MUST NOT expose setters or mutators.
 *
 * The DDD conformance architecture tests (`ValueObjectTest`) enforce this rule. Adding this
 * annotation commits the class to the rules defined in ADR-0017 (value object immutability).
 *
 * @since 1.0.0
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ValueObject
