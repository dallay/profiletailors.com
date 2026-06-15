package com.profiletailors.common.domain.model.pagination

/**
 * A generic page of results using cursor-based (keyset) pagination.
 *
 * Cursor pagination is preferred over offset pagination for live, frequently updated
 * datasets. Instead of a page number, the client uses opaque cursor strings to navigate:
 * the server returns a [nextPageCursor] and/or [prevPageCursor] that point to the
 * boundary items for the next or previous page.
 *
 * ## Cursor format
 * Cursors are opaque strings — their internal structure is an implementation detail
 * of the repository layer. They are typically base64-encoded composite values that
 * encode the sort position of the boundary item (e.g. `createdAt:ID`). Clients should
 * never parse or construct cursor strings manually.
 *
 * ## Mapping
 * - [map] transforms the entire data collection.
 * - [mapItems] applies a one-to-one transformation to each item.
 *
 * @param T the type of items in the page
 * @since 1.0.0
 * @see OffsetPage
 */
data class CursorPage<T>(
    val data: Collection<T>,
    val prevPageCursor: String? = null,
    val nextPageCursor: String? = null,
) {
    /**
     * Transforms the entire data collection while preserving cursor metadata.
     *
     * @param func the transformation function applied to the [data] collection
     * @return a new [CursorPage] with transformed data and unchanged cursors
     */
    inline fun <U> map(func: (Collection<T>) -> Collection<U>): CursorPage<U> = CursorPage(
        data = func(data),
        prevPageCursor = prevPageCursor,
        nextPageCursor = nextPageCursor,
    )

    /**
     * Transforms each item individually while preserving cursor metadata.
     *
     * Equivalent to `map { it.map(transform) }`.
     *
     * @param transform the transformation function applied to each item
     * @return a new [CursorPage] with transformed items
     */
    inline fun <U> mapItems(transform: (T) -> U): CursorPage<U> = map { it.map(transform) }
}
