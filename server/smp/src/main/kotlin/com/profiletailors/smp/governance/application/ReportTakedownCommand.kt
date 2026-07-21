package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.smp.governance.domain.TakedownReport

/**
 * Command to report a media asset for copyright/takedown.
 */
data class ReportTakedownCommand(
    val assetId: String,
    val reason: String,
    val reporterEmail: String,
    val mediaReferenceUrl: String? = null,
) : CommandWithResult<TakedownReport>
