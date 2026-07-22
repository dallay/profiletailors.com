package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.smp.governance.domain.TakedownReport

/**
 * Command to report a media asset for copyright/takedown.
 *
 * The [reporterEmail] is NOT passed here — [ReportTakedownHandler] derives it
 * from the authenticated principal's verified email address.
 */
data class ReportTakedownCommand(val assetId: String, val reason: String, val mediaReferenceUrl: String? = null) :
    CommandWithResult<TakedownReport>
