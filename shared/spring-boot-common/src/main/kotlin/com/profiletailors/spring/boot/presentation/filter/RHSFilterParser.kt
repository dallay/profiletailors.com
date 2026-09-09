package com.profiletailors.spring.boot.presentation.filter

import com.profiletailors.common.domain.criteria.Criteria
import com.profiletailors.common.domain.presentation.FilterInvalidException
import kotlinx.coroutines.CancellationException
import tools.jackson.databind.ObjectMapper
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

@Suppress("UNCHECKED_CAST")
class RHSFilterParser<T : Any>(private val clazz: KClass<T>, private val objectMapper: ObjectMapper) {
    private val regex = Regex("(.[^:]+):(.+)")

    /**
     * Parses filter query values into a combined criteria expression.
     *
     * @param query The properties and filter values to parse.
     * @param useOr Whether to combine the resulting criteria with OR instead of AND.
     * @return An empty, OR-combined, or AND-combined criteria expression.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun parse(query: Map<KProperty1<T, *>, Collection<String?>?>, useOr: Boolean = false): Criteria {
        try {
            val criteriaList: List<Criteria> = query
                .mapNotNull { (key, values) -> processQueryEntry(key, values) }
                .flatten()

            return when {
                criteriaList.isEmpty() -> Criteria.Empty
                useOr -> Criteria.Or(criteriaList)
                else -> Criteria.And(criteriaList)
            }
        } catch (e: FilterInvalidException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw FilterInvalidException(e.message)
        }
    }

    private fun processQueryEntry(key: KProperty1<T, *>, values: Collection<String?>?): List<Criteria> {
        val property = clazz.memberProperties.find { it == key } ?: return emptyList()
        val operandType = property.returnType.classifier as? KClass<*>
            ?: throw FilterInvalidException("Can't find operand type. Property: $property")
        return values?.filterNotNull()?.map { value -> processValue(property, operandType, value) } ?: emptyList()
    }

    private fun processValue(property: KProperty1<T, *>, clazz: KClass<*>, value: String): Criteria {
        val result = regex.find(value) ?: throw FilterInvalidException()
        val (operator, operand) = result.destructured
        val parsed = convert(operand, clazz)
        return create(property, operator, parsed)
    }

    /**
     * Converts an operand string to the specified Kotlin type.
     *
     * @param operand The string value to convert.
     * @param clazz The target Kotlin class.
     * @return The converted operand.
     * @throws FilterInvalidException If the operand cannot be converted to the target type.
     */
    private fun convert(operand: String, clazz: KClass<*>): Any {
        val candidates =
            listOfNotNull(operand, operand.toIntOrNull(), operand.toLongOrNull(), operand.toBooleanStrictOrNull())
        var converted: Any? = null
        for (candidate in candidates) {
            try {
                converted = objectMapper.convertValue(candidate, clazz.java)
                break
            } catch (e: CancellationException) {
                throw e
            } catch (_: RuntimeException) {
            }
        }
        if (converted == null) throw FilterInvalidException("Can't convert operand for type ${clazz.simpleName}")
        return converted
    }

    /**
     * Creates a criterion for a property using the specified operator and value.
     *
     * @param property The property to which the criterion applies.
     * @param operator The comparison operator.
     * @param value The value used by the criterion.
     * @return The criterion matching the operator.
     * @throws FilterInvalidException If the operator is unsupported.
     */
    private fun create(property: KProperty1<T, *>, operator: String, value: Any): Criteria = when (operator) {
        "ne" -> Criteria.NotEquals(property.name, value)
        "eq" -> Criteria.Equals(property.name, value)
        "lk" -> Criteria.Like(property.name, value as String)
        "ilk" -> Criteria.Ilike(property.name, value as String)
        "nl" -> Criteria.NotLike(property.name, value as String)
        "gt" -> Criteria.GreaterThan(property.name, value as Comparable<Any?>)
        "gte" -> Criteria.GreaterThanEquals(property.name, value as Comparable<Any?>)
        "lt" -> Criteria.LessThan(property.name, value as Comparable<Any?>)
        "lte" -> Criteria.LessThanEquals(property.name, value as Comparable<Any?>)
        else -> throw FilterInvalidException("Not support operator.")
    }
}
