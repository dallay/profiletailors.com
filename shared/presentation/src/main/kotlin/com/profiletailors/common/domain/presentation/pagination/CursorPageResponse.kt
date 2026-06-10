package com.profiletailors.common.domain.presentation.pagination

import com.profiletailors.common.domain.presentation.PageResponse

data class CursorPageResponse<T>(
    override val data: Collection<T>,
    val prevPageCursor: String?,
    val nextPageCursor: String?,
) : PageResponse<T>(data)

inline fun <T, U> CursorPageResponse<T>.map(
    func: (Collection<T>) -> Collection<U>
) = CursorPageResponse(
    data = func(data),
    prevPageCursor = prevPageCursor,
    nextPageCursor = nextPageCursor,
)
