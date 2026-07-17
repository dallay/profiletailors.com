package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.smp.governance.domain.ComplianceControlId
import com.profiletailors.smp.governance.domain.ComplianceControlStatus

data class RegisterComplianceControlCommand(
    val id: ComplianceControlId? = null,
    val controlKey: String,
    val name: String,
    val description: String? = null,
    val owner: String? = null,
    val category: String? = null,
    val status: ComplianceControlStatus = ComplianceControlStatus.ACTIVE,
) : Command
