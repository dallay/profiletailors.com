package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.smp.governance.domain.ConsentRecord

/** HTTP command whose workspace is resolved from the authenticated resource context. */
data class RecordWorkspaceConsentCommand(
    val subjectKind: String,
    val subjectValue: String,
    val consentType: String,
    val purpose: String,
    val policyVersion: String,
    val source: String,
    val locale: String,
) : CommandWithResult<RecordConsentOutcome>

data class WithdrawWorkspaceConsentCommand(
    val subjectKind: String,
    val subjectValue: String,
    val purpose: String,
    val policyVersion: String,
    val reason: String? = null,
) : CommandWithResult<ConsentRecord>

data class GetWorkspaceConsentRecordsQuery(val subjectKind: String? = null, val purpose: String? = null) :
    Query<List<ConsentRecord>>

data class GetConsentHistoryQuery(val subjectKind: String, val subjectValue: String, val purpose: String) :
    Query<List<ConsentRecord>>
