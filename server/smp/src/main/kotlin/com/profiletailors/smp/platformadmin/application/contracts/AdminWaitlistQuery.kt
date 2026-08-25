package com.profiletailors.smp.platformadmin.application.contracts

import com.profiletailors.smp.platformadmin.application.model.AdminWaitlistEntryDetail
import com.profiletailors.smp.platformadmin.application.model.AdminWaitlistEntrySummary
import com.profiletailors.smp.platformadmin.application.model.PagedResult
import com.profiletailors.smp.platformadmin.application.query.ListAdminWaitlistEntriesQuery

interface AdminWaitlistQuery {
    suspend fun list(query: ListAdminWaitlistEntriesQuery): PagedResult<AdminWaitlistEntrySummary>
    suspend fun findById(entryId: String): AdminWaitlistEntryDetail?
    suspend fun countByStatus(): Map<String, Long>
}
