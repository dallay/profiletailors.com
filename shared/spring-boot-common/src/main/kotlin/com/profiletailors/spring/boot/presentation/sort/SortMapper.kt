package com.profiletailors.spring.boot.presentation.sort

import com.profiletailors.common.domain.presentation.sort.Direction
import com.profiletailors.common.domain.presentation.sort.Sort
import org.springframework.data.domain.Sort as SpringSort

/**
 * Converts a nullable domain [Sort] into Spring Data's [SpringSort].
 *
 * A null domain sort maps to [SpringSort.unsorted], while each domain order is
 * converted to an equivalent Spring Data order preserving property name and
 * direction.
 *
 * @return Spring Data sort representation for repository queries.
 * @since 1.0.0
 */
fun Sort?.toSpringSort(): SpringSort {
    return this?.let { sort ->
        val orders = sort.orders.map { order ->
            when (order.direction) {
                Direction.ASC -> SpringSort.Order.asc(order.property)
                Direction.DESC -> SpringSort.Order.desc(order.property)
            }
        }
        SpringSort.by(orders)
    } ?: SpringSort.unsorted()
}
