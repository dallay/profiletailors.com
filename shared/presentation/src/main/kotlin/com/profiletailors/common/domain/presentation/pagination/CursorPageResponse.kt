package com.profiletailors.common.domain.presentation.pagination

import com.profiletailors.common.domain.presentation.PageResponse

/**
 * Cursor-based pagination page response.
 *
 * Use for infinite-scroll or "load more" patterns where offset-based
 * pagination would be inefficient. Cursors are opaque strings (Base64-encoded).
 *
 * @param T the type of items in the page
 * @param data the items on this page
 * @param prevPageCursor cursor to the previous page (null if this is the first page)
 * @param nextPageCursor cursor to the next page (null if this is the last page)
 */
data class CursorPageResponse<T>(
    override val data: Collection<T>,
    val prevPageCursor: String?,
    val nextPageCursor: String?,
) : PageResponse<T>(data)

inline fun <T, U> CursorPageResponse<T>.map(func: (Collection<T>) -> Collection<U>) = CursorPageResponse(
    data = func(data),
    prevPageCursor = prevPageCursor,
    nextPageCursor = nextPageCursor,
)
