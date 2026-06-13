package com.profiletailors.common.domain.presentation.pagination

import com.profiletailors.common.domain.presentation.PageResponse

/**
 * Offset-based pagination page response.
 *
 * Use for standard page-number navigation (e.g., "page 3 of 10").
 * The total count is optional to support cases where count queries are expensive.
 *
 * @param T the type of items in the page
 * @param data the items on this page
 * @param total total number of items across all pages (may be null if not queried)
 * @param perPage number of items per page
 * @param page current page number (1-based)
 * @param totalPages total number of pages
 */
data class OffsetPageResponse<T>(
    override val data: Collection<T>,
    val total: Long? = null,
    val perPage: Int,
    val page: Int? = null,
    val totalPages: Int? = null,
) : PageResponse<T>(data)

inline fun <T, U> OffsetPageResponse<T>.map(
    func: (Collection<T>) -> Collection<U>
) = OffsetPageResponse(
    data = func(data),
    total = total,
    perPage = perPage,
    page = page,
    totalPages = totalPages,
)
