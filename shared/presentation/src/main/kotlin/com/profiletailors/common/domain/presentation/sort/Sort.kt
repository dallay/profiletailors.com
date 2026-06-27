package com.profiletailors.common.domain.presentation.sort

/** Sort direction for query results. */
enum class Direction {
    ASC,
    DESC,
    ;

    fun fromString(direction: String): Direction = Direction.fromString(direction)
    fun reversed(): Direction = if (this == ASC) DESC else ASC
    companion object {
        fun fromString(direction: String): Direction = when (direction) {
            "asc" -> ASC
            "desc" -> DESC
            else -> throw IllegalArgumentException("Invalid direction: $direction")
        }
    }
}

/** A single sort criterion: a [property] sorted in the given [direction]. */
data class Order(val direction: Direction, val property: String) {
    override fun toString(): String = "$property: $direction"
    companion object {
        fun desc(property: String): Order = Order(Direction.DESC, property)
        fun asc(property: String): Order = Order(Direction.ASC, property)
    }
}

/**
 * Sort specification composed of one or more [Order] clauses.
 *
 * Usage:
 * ```kotlin
 * Sort.by("createdAt")
 * Sort.by(Direction.DESC, "priority", "createdAt")
 * Sort.by("name").and(Sort.by(Direction.DESC, "createdAt"))
 * ```
 */
class Sort(val orders: List<Order>) {
    fun and(sort: Sort): Sort = Sort(this.orders + sort.orders)
    fun ascending(): Sort = Sort(orders.map { it.copy(direction = Direction.ASC) })
    fun descending(): Sort = Sort(orders.map { it.copy(direction = Direction.DESC) })
    fun getOrderFor(property: String): Order? = orders.find { it.property == property }
    override fun toString(): String = orders.joinToString(", ") { "${it.property}: ${it.direction}" }

    companion object {
        val DEFAULT_DIRECTION = Direction.ASC
        fun by(vararg properties: String): Sort = Sort(properties.map { Order(DEFAULT_DIRECTION, it) })
        fun by(direction: Direction, vararg properties: String): Sort = Sort(properties.map { Order(direction, it) })
    }
}
