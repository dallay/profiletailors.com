package com.profiletailors.smp.governance.domain

import kotlinx.coroutines.flow.Flow

interface ComplianceEvidenceRepository {
    /**
 * Finds compliance evidence by its identifier.
 *
 * @param id The identifier of the compliance evidence.
 * @return The matching compliance evidence, or `null` if none exists.
 */
suspend fun findById(id: ComplianceEvidenceId): ComplianceEvidence?
    /**
 * Retrieves compliance evidence associated with a control.
 *
 * @param controlId The identifier of the compliance control.
 * @return A stream of compliance evidence associated with the control.
 */
fun findByControlId(controlId: ComplianceControlId): Flow<ComplianceEvidence>
    /**
 * Persists compliance evidence.
 *
 * @param evidence The compliance evidence to persist.
 * @return The persisted compliance evidence.
 */
suspend fun save(evidence: ComplianceEvidence): ComplianceEvidence
    /**
     * Links compliance evidence to a compliance control.
     *
     * @param controlId The identifier of the compliance control.
     * @param evidenceId The identifier of the compliance evidence.
     * @param linkedBy The actor or source that created the link.
     * @return The created control-evidence association.
     */
    suspend fun linkControlEvidence(
        controlId: ComplianceControlId,
        evidenceId: ComplianceEvidenceId,
        linkedBy: String,
    ): ComplianceControlEvidence
}
