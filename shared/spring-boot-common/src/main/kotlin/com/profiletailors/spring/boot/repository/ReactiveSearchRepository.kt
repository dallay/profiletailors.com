package com.profiletailors.spring.boot.repository

import com.profiletailors.common.domain.presentation.pagination.Cursor
import com.profiletailors.common.domain.presentation.pagination.CursorPageResponse
import kotlinx.coroutines.flow.Flow
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.relational.core.query.Criteria
import kotlin.reflect.KClass

/**
 * ReactiveSearchRepository is an interface for performing reactive database operations.
 * It provides methods for fetching all entities that match a given criteria, with support for pagination.
 *
 * @param T The type of the entity.
 */
interface ReactiveSearchRepository<T : Any> {

    /**
     * Fetches all entities that match the given criteria.
     *
     * @param criteria The criteria to match.
     * @param domainType The class of the entity.
     * @return A Flow of entities that match the criteria.
     */
    suspend fun findAll(criteria: Criteria, domainType: KClass<T>): Flow<T>

    /**
     * Fetches all entities that match the given criteria, with support for pagination.
     *
     * @param criteria The criteria to match.
     * @param pageable The pagination information.
     * @param domainType The class of the entity.
     * @return A Page of entities that match the criteria.
     */
    suspend fun findAll(criteria: Criteria, pageable: Pageable, domainType: KClass<T>): Page<T>

    /**
     * Fetches all entities that match the given criteria, with support for cursor-based pagination.
     *
     * @param criteria The criteria to match.
     * @param size The number of entities to fetch. Default is 10.
     * @param domainType The class of the entity.
     * @param sort The sort order.
     * @param cursor The cursor to use for pagination.
     * @return A [CursorPageResponse] of entities that match the criteria.
     */
    suspend fun findAllByCursor(
        criteria: Criteria,
        size: Int = 10,
        domainType: KClass<T>,
        sort: Sort,
        cursor: Cursor,
    ): CursorPageResponse<T>
}
