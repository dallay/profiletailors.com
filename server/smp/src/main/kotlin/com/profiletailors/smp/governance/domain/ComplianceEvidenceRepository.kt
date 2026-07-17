package com.profiletailors.smp.governance.domain

import kotlinx.coroutines.flow.Flow

interface ComplianceEvidenceRepository {
    suspend fun findById(id: ComplianceEvidenceId): ComplianceEvidence?
    fun findByControlId(controlId: ComplianceControlId): Flow<ComplianceEvidence>
    suspend fun save(evidence: ComplianceEvidence): ComplianceEvidence
    suspend fun linkControlEvidence(
        controlId: ComplianceControlId,
        evidenceId: ComplianceEvidenceId,
        linkedBy: String,
    ): ComplianceControlEvidence
}
