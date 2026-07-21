package com.profiletailors.smp.privacy.domain

import java.time.Duration
import java.time.Instant

/**
 * Aggregate root for a Data Subject Access Request (DSAR).
 *
 * Encapsulates the lifecycle of a DSAR including state transitions,
 * regulatory retention (30-day expiry from creation), and type-specific
 * payloads.
 *
 * @property id Unique identifier
 * @property requestType Type of DSAR (ACCESS, EXPORT, CORRECTION, DELETION)
 * @property status Current lifecycle status
 * @property requestedBy Principal ID of the requester
 * @property requestedByEmail Email snapshot at time of request
 * @property workspaceId Optional workspace scope (null for global/cross-workspace)
 * @property notes Optional user-provided context
 * @property correctionData JSON payload for CORRECTION requests
 * @property resultRef Reference to result (download URL or path)
 * @property rejectionReason Required when status is REJECTED
 * @property createdAt Timestamp of creation
 * @property updatedAt Timestamp of last update
 * @property completedAt Timestamp when status became COMPLETED
 * @property expiresAt Retention expiry (createdAt + 30 days)
 * @since 1.0.0
 */
data class DataSubjectRequest(
    val id: DataSubjectRequestId,
    val requestType: RequestType,
    val status: DataSubjectRequestStatus,
    val requestedBy: String,
    val requestedByEmail: String,
    val workspaceId: String?,
    val notes: String?,
    val correctionData: String?,
    val resultRef: String?,
    val rejectionReason: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant?,
    val expiresAt: Instant,
) {
    init {
        require(requestedBy.isNotBlank()) { "requestedBy must not be blank" }
        require(requestedByEmail.isNotBlank()) { "requestedByEmail must not be blank" }
        require(expiresAt.isAfter(createdAt)) { "expiresAt must be after createdAt" }
        if (status == DataSubjectRequestStatus.REJECTED) {
            require(!rejectionReason.isNullOrBlank()) { "rejectionReason is required when status is REJECTED" }
        }
        if (requestType == RequestType.CORRECTION) {
            require(!correctionData.isNullOrBlank()) { "correctionData is required for CORRECTION requests" }
        }
    }

    /**
     * Transitions this request to a new [target] status.
     *
     * @param target The target status
     * @param rejectionReason Required when transitioning to [DataSubjectRequestStatus.REJECTED]
     * @param completedAt Timestamp to set when transitioning to [DataSubjectRequestStatus.COMPLETED]
     * @return A new [DataSubjectRequest] with the updated status
     * @throws IllegalStateException if the transition is not valid
     * @throws IllegalArgumentException if [rejectionReason] is null/blank for REJECTED
     */
    fun transitionTo(
        target: DataSubjectRequestStatus,
        rejectionReason: String? = null,
        completedAt: Instant? = null,
    ): DataSubjectRequest {
        check(status.canTransitionTo(target)) {
            "Cannot transition from $status to $target"
        }
        if (target == DataSubjectRequestStatus.REJECTED) {
            require(!rejectionReason.isNullOrBlank()) {
                "rejectionReason is required when transitioning to REJECTED"
            }
        }
        return copy(
            status = target,
            rejectionReason = if (target == DataSubjectRequestStatus.REJECTED) rejectionReason else null,
            completedAt = if (target == DataSubjectRequestStatus.COMPLETED) {
                completedAt ?: Instant.now()
            } else {
                null
            },
            updatedAt = Instant.now(),
        )
    }

    companion object {
        private const val RETENTION_DAYS = 30L

        /**
         * Factory method to create a new [DataSubjectRequest] in [DataSubjectRequestStatus.PENDING].
         *
         * @param id Unique identifier
         * @param requestType Type of DSAR
         * @param requestedBy Principal ID of the requester
         * @param requestedByEmail Email snapshot
         * @param workspaceId Optional workspace scope
         * @param notes Optional notes
         * @param correctionData JSON payload for CORRECTION
         * @param createdAt Creation timestamp (defaults to now)
         * @return A new [DataSubjectRequest] with [DataSubjectRequestStatus.PENDING] and
         *         [expiresAt] set to [createdAt] + 30 days
         */
        @Suppress("S107")
        fun create(
            id: DataSubjectRequestId,
            requestType: RequestType,
            requestedBy: String,
            requestedByEmail: String,
            workspaceId: String? = null,
            notes: String? = null,
            correctionData: String? = null,
            createdAt: Instant = Instant.now(),
        ): DataSubjectRequest = DataSubjectRequest(
            id = id,
            requestType = requestType,
            status = DataSubjectRequestStatus.PENDING,
            requestedBy = requestedBy,
            requestedByEmail = requestedByEmail,
            workspaceId = workspaceId,
            notes = notes,
            correctionData = correctionData,
            resultRef = null,
            rejectionReason = null,
            createdAt = createdAt,
            updatedAt = createdAt,
            completedAt = null,
            expiresAt = createdAt.plus(Duration.ofDays(RETENTION_DAYS)),
        )
    }
}
