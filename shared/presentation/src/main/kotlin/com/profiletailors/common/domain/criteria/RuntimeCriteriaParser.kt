package com.profiletailors.common.domain.criteria

import com.profiletailors.common.domain.regexp.SqlLikeTranspiler
import java.util.regex.Pattern
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

@Suppress("MethodOverloading")
class RuntimeCriteriaParser<T : Any>(
    clazz: KClass<T>
) : CriteriaParser<((T) -> Boolean)?> {
    private val properties = clazz.memberProperties.associateBy { it.name }

    @Suppress("CyclomaticComplexMethod", "CognitiveComplexMethod")
    override fun parse(criteria: Criteria): ((T) -> Boolean)? = when (criteria) {
        is Criteria.Empty -> null
        is Criteria.And -> parse(criteria)
        is Criteria.Or -> parse(criteria)
        is Criteria.Equals -> parse(criteria)
        is Criteria.NotEquals -> parse(criteria)
        is Criteria.Between -> parse(criteria)
        is Criteria.NotBetween -> parse(criteria)
        is Criteria.LessThan -> parse(criteria)
        is Criteria.LessThanEquals -> parse(criteria)
        is Criteria.GreaterThan -> parse(criteria)
        is Criteria.GreaterThanEquals -> parse(criteria)
        is Criteria.IsNull -> parse(criteria)
        is Criteria.IsNotNull -> parse(criteria)
        is CriteriaLike -> parse(criteria)
        is Criteria.NotLike -> parse(criteria)
        is Criteria.Regexp -> parse(criteria)
        is Criteria.NotRegexp -> parse(criteria)
        is Criteria.In -> parse(criteria)
        is Criteria.NotIn -> parse(criteria)
        is Criteria.IsTrue -> parse(criteria)
        is Criteria.IsFalse -> parse(criteria)
    }

    private fun parse(criteria: Criteria.And): ((T) -> Boolean)? {
        if (criteria.value.isEmpty()) return null
        return criteria.value.mapNotNull { parse(it) }.reduce { acc, cur -> { acc(it) && cur(it) } }
    }

    private fun parse(criteria: Criteria.Or): ((T) -> Boolean)? {
        if (criteria.value.isEmpty()) return null
        return criteria.value.mapNotNull { parse(it) }.reduce { acc, cur -> { acc(it) || cur(it) } }
    }

    private fun parse(criteria: Criteria.Equals): (T) -> Boolean = {
        properties[criteria.key]?.let { p -> p.get(it) == criteria.value } == true
    }

    private fun parse(criteria: Criteria.NotEquals): (T) -> Boolean = {
        properties[criteria.key]?.let { p -> p.get(it) != criteria.value } == true
    }

    private fun parse(criteria: Criteria.Between): (T) -> Boolean = {
        properties[criteria.key]?.let { p ->
            p.get(it)?.let { v ->
                if (v is Comparable<*>) {
                    @Suppress("UNCHECKED_CAST")
                    v as Comparable<Any?>
                    v >= criteria.value.start && v <= criteria.value.endInclusive
                } else false
            } == true
        } == true
    }

    private fun parse(criteria: Criteria.NotBetween): (T) -> Boolean = {
        properties[criteria.key]?.let { p ->
            p.get(it)?.let { v ->
                if (v is Comparable<*>) {
                    @Suppress("UNCHECKED_CAST")
                    v as Comparable<Any?>
                    v < criteria.value.start || v > criteria.value.endInclusive
                } else false
            } != false
        } == true
    }

    private fun parse(criteria: Criteria.LessThan): (T) -> Boolean = {
        properties[criteria.key]?.let { p ->
            p.get(it)?.let { v ->
                if (v is Comparable<*>) {
                    @Suppress("UNCHECKED_CAST")
                    v as Comparable<Any?>
                    v < criteria.value
                } else false
            } == true
        } == true
    }

    private fun parse(criteria: Criteria.LessThanEquals): (T) -> Boolean = {
        properties[criteria.key]?.let { p ->
            p.get(it)?.let { v ->
                if (v is Comparable<*>) {
                    @Suppress("UNCHECKED_CAST")
                    v as Comparable<Any?>
                    v <= criteria.value
                } else false
            } == true
        } == true
    }

    private fun parse(criteria: Criteria.GreaterThan): (T) -> Boolean = {
        properties[criteria.key]?.let { p ->
            p.get(it)?.let { v ->
                if (v is Comparable<*>) {
                    @Suppress("UNCHECKED_CAST")
                    v as Comparable<Any?>
                    v > criteria.value
                } else false
            } == true
        } == true
    }

    private fun parse(criteria: Criteria.GreaterThanEquals): (T) -> Boolean = {
        properties[criteria.key]?.let { p ->
            p.get(it)?.let { v ->
                if (v is Comparable<*>) {
                    @Suppress("UNCHECKED_CAST")
                    v as Comparable<Any?>
                    v >= criteria.value
                } else false
            } == true
        } == true
    }

    private fun parse(criteria: Criteria.IsNull): (T) -> Boolean = {
        properties[criteria.key]?.let { p -> p.get(it) == null } == true
    }

    private fun parse(criteria: Criteria.IsNotNull): (T) -> Boolean = {
        properties[criteria.key]?.let { p -> p.get(it) != null } == true
    }

    private fun parse(criteria: CriteriaLike, ignoreCase: Boolean = false): (T) -> Boolean {
        val regexFlags = if (ignoreCase) Pattern.CASE_INSENSITIVE else 0
        val pattern = Pattern.compile(SqlLikeTranspiler.toRegEx(criteria.value), regexFlags)
        return {
            properties[criteria.key]?.get(it)?.let { v ->
                v is CharSequence && pattern.matcher(v).find()
            } == true
        }
    }

    private fun parse(criteria: Criteria.Like): (T) -> Boolean = parse(criteria, ignoreCase = false)
    private fun parse(criteria: Criteria.Ilike): (T) -> Boolean = parse(criteria, ignoreCase = true)

    private fun parse(criteria: Criteria.NotLike): (T) -> Boolean {
        val pattern = Pattern.compile(SqlLikeTranspiler.toRegEx(criteria.value))
        return {
            properties[criteria.key]?.get(it)?.let { v ->
                v is CharSequence && !pattern.matcher(v).find()
            } == true
        }
    }

    private fun parse(criteria: Criteria.Regexp): (T) -> Boolean = {
        properties[criteria.key]?.get(it)?.let { v ->
            v is CharSequence && criteria.value.matcher(v).find()
        } == true
    }

    private fun parse(criteria: Criteria.NotRegexp): (T) -> Boolean = {
        properties[criteria.key]?.get(it)?.let { v ->
            v is CharSequence && !criteria.value.matcher(v).find()
        } == true
    }

    private fun parse(criteria: Criteria.In): (T) -> Boolean = {
        properties[criteria.key]?.get(it)?.let { v -> criteria.value.contains(v) } == true
    }

    private fun parse(criteria: Criteria.NotIn): (T) -> Boolean = {
        properties[criteria.key]?.get(it)?.let { v -> !criteria.value.contains(v) } == true
    }

    private fun parse(criteria: Criteria.IsTrue): (T) -> Boolean = {
        properties[criteria.key]?.get(it)?.let { v -> v == true } == true
    }

    private fun parse(criteria: Criteria.IsFalse): (T) -> Boolean = {
        properties[criteria.key]?.get(it)?.let { v -> v == false } == true
    }
}
