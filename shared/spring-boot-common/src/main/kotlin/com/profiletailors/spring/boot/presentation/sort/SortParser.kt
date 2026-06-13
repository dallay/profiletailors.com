package com.profiletailors.spring.boot.presentation.sort

import com.profiletailors.common.domain.presentation.SortInvalidException
import com.profiletailors.common.domain.presentation.sort.Sort
import com.profiletailors.spring.boot.repository.columnName
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.introspect.AnnotatedField

/**
 * Parses API sort expressions into domain [Sort] objects for a target DTO/entity type.
 *
 * Sort expressions use the `direction:property` format, for example `asc:name`
 * or `desc:createdAt`. The parser validates that the requested property is
 * exported by [clazz], applies Jackson naming strategy when configured, and maps
 * the property to its database column name via [columnName].
 *
 * @param T Type whose properties are allowed in sort expressions.
 * @property clazz Kotlin class used for property validation and column mapping.
 * @property objectMapper Mapper used to honor Jackson property naming strategy.
 * @since 1.0.0
 */
class SortParser<T : Any>(
    private val clazz: KClass<T>,
    private val objectMapper: ObjectMapper,
) {
    private val regex = Regex("(.[^:]+):(.+)")

    fun parse(sort: Collection<String>): Sort {
        return sort
            .map { parse(it) }
            .reduce { acc, cur -> acc.and(cur) }
    }

    fun parse(sort: String): Sort {
        try {
            val result = regex.find(sort) ?: throw SortInvalidException()
            val (direction, property) = result.destructured

            val memberProperty = clazz.memberProperties.find {
                exportedPropertyName(it) == property
            } ?: throw SortInvalidException()

            return Sort.by(
                Sort.DEFAULT_DIRECTION.fromString(direction),
                columnName(memberProperty),
            )
        } catch (e: IllegalArgumentException) {
            throw SortInvalidException(e.message, e)
        }
    }

    private fun exportedPropertyName(property: KProperty<*>): String {
        val config = objectMapper.serializationConfig()
        val namingStrategy = config.propertyNamingStrategy
        return if (namingStrategy != null && property.javaField != null) {
            val annotatedField = AnnotatedField(null, property.javaField, null)
            namingStrategy.nameForField(
                config,
                annotatedField,
                property.name,
            )
        } else {
            property.name
        }
    }
}
