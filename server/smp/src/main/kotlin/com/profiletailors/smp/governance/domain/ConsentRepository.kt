package com.profiletailors.smp.governance.domain

import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Persistence port for versioned consent and legal-evidence records.
 *
 * All query methods are scoped to a workspace reference. The repository must
 * enforce this boundary so that one workspace can never read another's consent
 * records even when subject identifiers collide.
 *
 * Records are append-only: [save] never updates an existing row. Withdrawal is
 * modelled by persisting a new record derived from [ConsentRecord.withdraw]
 * that carries the same identity and updated status — historical rows remain
 * queryable via [findHistoricalByIdentity].
 */
interface ConsentRepository {

    /**
     * Persists a new consent record.
     *
     * @param record The record to persist.
     * @return The persisted record.
     */
    suspend fun save(record: ConsentRecord): ConsentRecord

    /**
     * Finds a consent record by its primary identifier.
     *
     * @param id The record identifier.
     * @return The matching record or `null` if no row exists.
     */
    suspend fun findById(id: ConsentRecordId): ConsentRecord?

    /**
     * Returns the active (non-withdrawn) consent for the given subject + purpose + version triple.
     *
     * Implementations MUST scope by [workspaceId] so callers cannot leak across
     * workspace boundaries.
     *
     * @return The active record, or `null` if none exists for the supplied triple.
     */
    suspend fun findActive(
        workspaceId: String,
        subjectReference: SubjectReference,
        purpose: String,
        policyVersion: String,
    ): ConsentRecord?

    /**
     * Streams the active records owned by a workspace, optionally filtered by
     * subject kind or purpose.
     */
    fun findActiveByWorkspace(
        workspaceId: String,
        subjectKind: SubjectKind? = null,
        purpose: String? = null,
    ): Flow<ConsentRecord>

    /**
     * Returns every record (including withdrawn ones) for the supplied identity,
     * ordered by [ConsentRecord.givenAt] ascending.
     *
     * Identity is the subject reference + purpose combination, scoped to a
     * workspace.
     */
    fun findHistoricalByIdentity(
        workspaceId: String,
        subjectReference: SubjectReference,
        purpose: String,
    ): Flow<ConsentRecord>

    /**
     * Returns whether an active record exists for the identity triple.
     *
     * Used by the idempotency check on [RecordConsentCommand] before persisting.
     */
    suspend fun existsActive(
        workspaceId: String,
        subjectReference: SubjectReference,
        purpose: String,
        policyVersion: String,
    ): Boolean

    /**
     * Default instant provider used for tests and fallback clock.
     */
    fun now(): Instant = Instant.now()
}
