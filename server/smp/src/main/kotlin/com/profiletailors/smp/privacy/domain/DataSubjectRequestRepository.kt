package com.profiletailors.smp.privacy.domain

import java.time.Instant

/**
 * Repository contract for [DataSubjectRequest] persistence.
 *
 * Defined in the domain layer following hexagonal architecture (port).
 * Implementations reside in the infrastructure layer.
 *
 * @since 1.0.0
 */
interface DataSubjectRequestRepository {

    /**
     * Persist a [DataSubjectRequest] (insert or update).
     */
    suspend fun save(request: DataSubjectRequest)

    /**
     * Find a request by its [id].
     *
     * @return the request if found, or `null` if no request with that id exists.
     */
    suspend fun findById(id: String): DataSubjectRequest?

    /**
     * Find all requests submitted by a given principal.
     */
    suspend fun findByRequester(principalId: String): List<DataSubjectRequest>

    /**
     * Find all requests with a given [status].
     */
    suspend fun findByStatus(status: DataSubjectRequestStatus): List<DataSubjectRequest>

    /**
     * Find all requests whose [DataSubjectRequest.expiresAt] is before [before].
     *
     * Used by the expiry job to identify requests eligible for TTL deletion.
     */
    suspend fun findExpired(before: Instant): List<DataSubjectRequest>
}
