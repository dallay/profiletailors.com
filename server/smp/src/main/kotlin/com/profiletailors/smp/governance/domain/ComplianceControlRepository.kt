package com.profiletailors.smp.governance.domain

import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class PageRequest(val limit: Int, val offset: Long = 0)

interface ComplianceControlRepository {
    /**
     * Finds a compliance control by its identifier.
     *
     * @param id The identifier of the compliance control.
     * @return The matching compliance control, or `null` if no control is found.
     */
    suspend fun findById(id: ComplianceControlId): ComplianceControl?

    /**
     * Streams compliance controls for the specified page.
     *
     * @param page The pagination parameters defining the maximum number of controls and starting position.
     * @return A stream of compliance controls in the requested page.
     */
    fun findAll(page: PageRequest): Flow<ComplianceControl>

    /**
     * Finds compliance controls applicable to an evaluation context at a specified time.
     *
     * @param context The context used to determine control applicability.
     * @param evaluatedAt The timestamp at which applicability is evaluated.
     * @return A stream of applicable compliance controls.
     */
    fun findApplicable(context: ComplianceEvaluationContext, evaluatedAt: Instant): Flow<ApplicableComplianceControl>

    /**
     * Persists a compliance control.
     *
     * @param control The compliance control to persist.
     * @return The persisted compliance control.
     */
    suspend fun save(control: ComplianceControl): ComplianceControl
}
