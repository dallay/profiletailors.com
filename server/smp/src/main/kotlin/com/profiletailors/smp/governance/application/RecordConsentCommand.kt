package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.smp.governance.domain.ConsentType
import com.profiletailors.smp.governance.domain.SubjectReference

/** Command to record a versioned consent, contract acceptance or legitimate-interest record. */
data class RecordConsentCommand(
    val workspaceId: String,
    val subjectReference: SubjectReference,
    val consentType: ConsentType,
    val purpose: String,
    val policyVersion: String,
    val source: String,
    val locale: String,
) : Command
