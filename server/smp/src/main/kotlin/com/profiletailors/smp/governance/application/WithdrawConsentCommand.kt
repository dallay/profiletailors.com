package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.smp.governance.domain.SubjectReference

/** Command to withdraw an active consent record without deleting historical evidence. */
data class WithdrawConsentCommand(
    val workspaceId: String,
    val subjectReference: SubjectReference,
    val purpose: String,
    val policyVersion: String,
    val reason: String? = null,
) : Command
