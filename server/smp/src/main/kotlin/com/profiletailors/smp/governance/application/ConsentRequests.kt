package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.smp.governance.domain.ConsentRecord
import com.profiletailors.smp.governance.domain.ConsentType
import com.profiletailors.smp.governance.domain.SubjectKind
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

data class RecordBootstrapTermsConsentCommand(
    val principalId: UUID,
    val termsVersion: String,
    val occurredAt: Instant,
) : Command

data class CheckBootstrapTermsConsentQuery(val principalId: UUID, val termsVersion: String) : Query<Boolean>

/** HTTP command whose workspace is resolved from the authenticated resource context. */
data class RecordWorkspaceConsentCommand(
    val subjectKind: SubjectKind,
    val subjectValue: String,
    val consentType: ConsentType,
    val purpose: String,
    val policyVersion: String,
    val source: String,
    val locale: String,
) : CommandWithResult<RecordConsentOutcome>

data class WithdrawWorkspaceConsentCommand(
    val subjectKind: SubjectKind,
    val subjectValue: String,
    val purpose: String,
    val policyVersion: String,
    val reason: String? = null,
) : CommandWithResult<ConsentRecord>

data class GetWorkspaceConsentRecordsQuery(val subjectKind: SubjectKind? = null, val purpose: String? = null) :
    Query<Flow<ConsentRecord>>

data class GetConsentHistoryQuery(val subjectKind: SubjectKind, val subjectValue: String, val purpose: String) :
    Query<Flow<ConsentRecord>>
