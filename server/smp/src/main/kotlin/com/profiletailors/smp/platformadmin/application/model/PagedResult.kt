package com.profiletailors.smp.platformadmin.application.model

data class PagedResult<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
) {
    companion object {
        fun <T> of(items: List<T>, page: Int, size: Int, totalElements: Long): PagedResult<T> {
            val totalPages = if (size > 0) {
                val pages = (totalElements + size.toLong() - 1) / size.toLong()
                when {
                    pages > Int.MAX_VALUE -> Int.MAX_VALUE
                    else -> pages.toInt()
                }
            } else {
                0
            }
            return PagedResult(
                items = items,
                page = page,
                size = size,
                totalElements = totalElements,
                totalPages = totalPages,
                hasNext = page < totalPages - 1,
                hasPrevious = page > 0,
            )
        }
    }
}
