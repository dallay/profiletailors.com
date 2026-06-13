package com.profiletailors.common.domain.criteria

import java.util.regex.Pattern

/**
 * Base class for LIKE-pattern criteria.
 *
 * @param key the field name
 * @param value the pattern string (SQL LIKE syntax)
 */
open class CriteriaLike(open val key: String, open val value: String) : Criteria() {
    override fun toString(): String = "$key LIKE $value"
}

/**
 * Criteria expression tree for dynamic query filtering.
 *
 * Build filter conditions programmatically without SQL injection risk:
 * ```kotlin
 * val filter = Criteria.And(listOf(
 *     Criteria.Equals("status", "published"),
 *     Criteria.Or(listOf(
 *         Criteria.LessThan("priority", 5),
 *         Criteria.Equals("featured", true)
 *     ))
 * ))
 * ```
 *
 * Parsed from strings via [CriteriaParser][com.profiletailors.common.domain.criteria.CriteriaParser].
 * Transpiled to R2DBC predicates via [R2DBCCriteriaParser][com.profiletailors.spring.boot.repository.R2DBCCriteriaParser].
 */
sealed class Criteria {

    /** Empty criteria — matches everything. */
    object Empty : Criteria() {
        override fun toString(): String = "()"
    }

    /** All inner criteria must match (logical AND). */
    data class And(val value: List<Criteria>) : Criteria() {
        override fun toString(): String = "(${value.joinToString(" AND ") { it.toString() }})"
    }

    /** At least one inner criteria must match (logical OR). */
    data class Or(val value: List<Criteria>) : Criteria() {
        override fun toString(): String = "(${value.joinToString(" OR ") { it.toString() }})"
    }

    /** Field equals value. */
    data class Equals(val key: String, val value: Any) : Criteria() {
        override fun toString(): String = "$key = $value"
    }

    /** Field does not equal value. */
    data class NotEquals(val key: String, val value: Any) : Criteria() {
        override fun toString(): String = "$key != $value"
    }

    /** Field is within a closed range. */
    data class Between(val key: String, val value: ClosedRange<*>) : Criteria() {
        override fun toString(): String = "($key >= ${value.start} && $key <= ${value.endInclusive})"
    }

    /** Field is outside a closed range. */
    data class NotBetween(val key: String, val value: ClosedRange<*>) : Criteria() {
        override fun toString(): String = "($key < ${value.start} || $key > ${value.endInclusive})"
    }

    /** Field is strictly less than value. */
    data class LessThan(val key: String, val value: Any) : Criteria() {
        override fun toString(): String = "$key < $value"
    }

    /** Field is less than or equal to value. */
    data class LessThanEquals(val key: String, val value: Any) : Criteria() {
        override fun toString(): String = "$key <= $value"
    }

    /** Field is strictly greater than value. */
    data class GreaterThan(val key: String, val value: Any) : Criteria() {
        override fun toString(): String = "$key > $value"
    }

    /** Field is greater than or equal to value. */
    data class GreaterThanEquals(val key: String, val value: Any) : Criteria() {
        override fun toString(): String = "$key >= $value"
    }
    data class IsNull(val key: String) : Criteria() {
        override fun toString(): String = "$key = null"
    }
    data class IsNotNull(val key: String) : Criteria() {
        override fun toString(): String = "$key != null"
    }
    data class Like(override val key: String, override val value: String) : CriteriaLike(key, value) {
        override fun toString(): String = "$key LIKE $value"
    }
    data class Ilike(override val key: String, override val value: String) : CriteriaLike(key, value) {
        override fun toString(): String = "$key ILIKE $value"
    }
    data class NotLike(val key: String, val value: String) : Criteria() {
        override fun toString(): String = "$key NOT LIKE $value"
    }
    data class Regexp(val key: String, val value: Pattern) : Criteria() {
        override fun toString(): String = "$key REGEXP $value"
    }
    data class NotRegexp(val key: String, val value: Pattern) : Criteria() {
        override fun toString(): String = "$key NOT REGEXP $value"
    }
    data class In(val key: String, val value: List<Any?>) : Criteria() {
        override fun toString(): String = "$key IN [${value.joinToString { it?.toString() ?: "null" }}]"
    }
    data class NotIn(val key: String, val value: List<Any?>) : Criteria() {
        override fun toString(): String = "$key NOT IN [${value.joinToString { it?.toString() ?: "null" }}]"
    }
    data class IsTrue(val key: String) : Criteria() {
        override fun toString(): String = "$key IS TRUE"
    }
    data class IsFalse(val key: String) : Criteria() {
        override fun toString(): String = "$key IS FALSE"
    }
}
