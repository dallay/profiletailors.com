package com.profiletailors.common.domain.presentation

import com.profiletailors.common.domain.bus.query.Response

/**
 * Generic page response envelope for paginated query results.
 *
 * Base class for [OffsetPageResponse][com.profiletailors.common.domain.presentation.pagination.OffsetPageResponse]
 * and [CursorPageResponse][com.profiletailors.common.domain.presentation.pagination.CursorPageResponse].
 *
 * @param T the type of items in the page
 */
open class PageResponse<T>(
    open val data: Collection<T>
) : Response
