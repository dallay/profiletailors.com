package com.profiletailors.common.domain.model.pagination

data class CursorPage<T>(
    val data: Collection<T>,
    val prevPageCursor: String? = null,
    val nextPageCursor: String? = null,
) {
    inline fun <U> map(func: (Collection<T>) -> Collection<U>): CursorPage<U> = CursorPage(
        data = func(data),
        prevPageCursor = prevPageCursor,
        nextPageCursor = nextPageCursor,
    )

    inline fun <U> mapItems(transform: (T) -> U): CursorPage<U> = map { it.map(transform) }
}
