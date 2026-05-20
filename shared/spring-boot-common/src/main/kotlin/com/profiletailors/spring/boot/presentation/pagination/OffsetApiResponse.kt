package com.profiletailors.spring.boot.presentation.pagination

import com.profiletailors.spring.boot.presentation.ApiEnvelope

data class OffsetApiResponse<T>(
    override val data: Collection<T>,
    val total: Long? = null,
    val perPage: Int,
    val page: Int? = null,
    val totalPages: Int? = null,
    override val message: String = "Operation successful",
) : ApiEnvelope<Collection<T>>(message, data)

inline fun <T, U> OffsetApiResponse<T>.map(
    func: (Collection<T>) -> Collection<U>,
) = OffsetApiResponse(
    data = func(data),
    total = total,
    perPage = perPage,
    page = page,
    totalPages = totalPages,
    message = message,
)
