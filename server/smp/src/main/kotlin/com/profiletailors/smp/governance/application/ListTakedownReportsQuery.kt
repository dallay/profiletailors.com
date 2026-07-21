package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.smp.governance.domain.TakedownReport
import com.profiletailors.smp.governance.domain.TakedownReportStatus
import kotlinx.coroutines.flow.Flow

/**
 * Query to list takedown reports for the current workspace.
 */
data class ListTakedownReportsQuery(val status: TakedownReportStatus? = null) : Query<Flow<TakedownReport>>
