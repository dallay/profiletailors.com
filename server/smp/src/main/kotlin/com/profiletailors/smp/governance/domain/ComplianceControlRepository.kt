package com.profiletailors.smp.governance.domain

import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class PageRequest(val limit: Int, val offset: Long = 0)

interface ComplianceControlRepository {
    suspend fun findById(id: ComplianceControlId): ComplianceControl?
    fun findAll(page: PageRequest): Flow<ComplianceControl>
    fun findApplicable(context: ComplianceEvaluationContext, evaluatedAt: Instant): Flow<ApplicableComplianceControl>
    suspend fun save(control: ComplianceControl): ComplianceControl
}
