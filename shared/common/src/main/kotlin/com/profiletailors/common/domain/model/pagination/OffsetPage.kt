package com.profiletailors.common.domain.model.pagination

data class OffsetPage<T>(
    val data: Collection<T>,
    val total: Long? = null,
    val perPage: Int,
    val page: Int? = null,
    val totalPages: Int? = null,
) {
    inline fun <U> map(func: (Collection<T>) -> Collection<U>): OffsetPage<U> = OffsetPage(
        data = func(data),
        total = total,
        perPage = perPage,
        page = page,
        totalPages = totalPages,
    )

    inline fun <U> mapItems(transform: (T) -> U): OffsetPage<U> = map { it.map(transform) }
}
