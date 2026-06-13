package com.profiletailors.common.domain.model.pagination

/**
 * A generic page of results using offset-based pagination.
 *
 * Offset pagination is suitable for stable datasets where the caller knows the page
 * number and items per page. The server returns a slice of the total result set.
 *
 * ## Field semantics
 * - [data]: the items for the current page.
 * - [total]: the total number of matching items in the full result set (`null` if
 *   the total was not computed, typically for performance reasons).
 * - [perPage]: the maximum number of items requested per page.
 * - [page]: the current page number (1-based), or `null` if not tracked.
 * - [totalPages]: the total number of pages derived from [total] and [perPage],
 *   or `null` if [total] was not computed.
 *
 * ## Mapping
 * - [map] transforms the entire data collection (e.g. for pagination metadata enrichment).
 * - [mapItems] applies a one-to-one transformation to each item (e.g. domain-to-DTO mapping).
 *
 * @param T the type of items in the page
 * @since 1.0.0
 * @see CursorPage
 */
data class OffsetPage<T>(
    val data: Collection<T>,
    val total: Long? = null,
    val perPage: Int,
    val page: Int? = null,
    val totalPages: Int? = null,
) {
    /**
     * Transforms the entire data collection while preserving pagination metadata.
     *
     * Use this when you need to change the collection structure (e.g. sorting, filtering,
     * or converting the list type).
     *
     * @param func the transformation function applied to the [data] collection
     * @return a new [OffsetPage] with transformed data and unchanged pagination fields
     */
    inline fun <U> map(func: (Collection<T>) -> Collection<U>): OffsetPage<U> = OffsetPage(
        data = func(data),
        total = total,
        perPage = perPage,
        page = page,
        totalPages = totalPages,
    )

    /**
     * Transforms each item individually while preserving pagination metadata.
     *
     * Equivalent to `map { it.map(transform) }`. Prefer this over [map] for
     * straightforward one-to-one item transformations (e.g. entity → DTO).
     *
     * @param transform the transformation function applied to each item
     * @return a new [OffsetPage] with transformed items
     */
    inline fun <U> mapItems(transform: (T) -> U): OffsetPage<U> = map { it.map(transform) }
}
