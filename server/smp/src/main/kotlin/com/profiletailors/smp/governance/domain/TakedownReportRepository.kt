package com.profiletailors.smp.governance.domain

import kotlinx.coroutines.flow.Flow

/**
 * Repository for [TakedownReport] persistence.
 */
interface TakedownReportRepository {

    /**
     * Persists a new takedown report.
     */
    suspend fun save(report: TakedownReport): TakedownReport

    /**
     * Finds a takedown report by its ID within a workspace.
     */
    suspend fun findById(workspaceId: String, reportId: String): TakedownReport?

    /**
     * Lists takedown reports for a workspace, optionally filtered by status.
     */
    fun findByWorkspace(workspaceId: String, status: TakedownReportStatus? = null): Flow<TakedownReport>
}
