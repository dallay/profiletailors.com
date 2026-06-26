package com.profiletailors.spring.boot.presentation.pagination

import com.profiletailors.spring.boot.presentation.ApiEnvelope

data class CursorApiResponse<T>(
    override val data: Collection<T>,
    val prevPageCursor: String?,
    val nextPageCursor: String?,
    override val message: String = "Operation successful",
) : ApiEnvelope<Collection<T>>(message, data)

inline fun <T, U> CursorApiResponse<T>.map(func: (Collection<T>) -> Collection<U>) = CursorApiResponse(
    data = func(data),
    prevPageCursor = prevPageCursor,
    nextPageCursor = nextPageCursor,
    message = message,
)
