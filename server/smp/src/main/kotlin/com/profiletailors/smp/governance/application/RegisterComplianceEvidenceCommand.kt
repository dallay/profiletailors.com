package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.bus.command.Command

data class RegisterComplianceEvidenceCommand(
    val evidenceType: String,
    val title: String,
    val description: String? = null,
    val referenceUrl: String? = null,
    val immutableReference: String? = null,
    val checksum: String? = null,
    val metadataJson: String? = null,
    val submittedBy: String,
) : Command
