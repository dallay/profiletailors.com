package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.smp.governance.domain.TakedownReport

/**
 * Command to approve a pending takedown report.
 */
data class ApproveTakedownCommand(val reportId: String) : CommandWithResult<TakedownReport>
