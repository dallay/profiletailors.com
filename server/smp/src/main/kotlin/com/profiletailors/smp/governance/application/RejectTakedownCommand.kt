package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.smp.governance.domain.TakedownReport

/**
 * Command to reject/dismiss a pending takedown report.
 */
data class RejectTakedownCommand(val reportId: String, val reason: String) : CommandWithResult<TakedownReport>
