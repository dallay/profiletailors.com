package com.profiletailors.smp.governance.domain

import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface ConsentRepository {
    suspend fun save(record: ConsentRecord): ConsentRecord

    suspend fun recordActiveReturning(record: ConsentRecord): Pair<Boolean, ConsentRecord>

    suspend fun withdrawActiveReturning(
        scope: ConsentScope,
        subjectReference: SubjectReference,
        purpose: String,
        policyVersion: String,
        withdrawnAt: Instant,
        reason: String?,
    ): ConsentRecord?

    suspend fun findById(scope: ConsentScope, id: ConsentRecordId): ConsentRecord?

    suspend fun findActive(
        scope: ConsentScope,
        subjectReference: SubjectReference,
        purpose: String,
        policyVersion: String,
    ): ConsentRecord?

    fun findActiveByScope(
        scope: ConsentScope,
        subjectKind: SubjectKind? = null,
        purpose: String? = null,
    ): Flow<ConsentRecord>

    fun findHistoricalByIdentity(
        scope: ConsentScope,
        subjectReference: SubjectReference,
        purpose: String,
    ): Flow<ConsentRecord>

    suspend fun existsActive(
        scope: ConsentScope,
        subjectReference: SubjectReference,
        purpose: String,
        policyVersion: String,
    ): Boolean

    fun now(): Instant = Instant.now()
}
