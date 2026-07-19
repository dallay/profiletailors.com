package com.profiletailors.smp.privacy.application

import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.query.Query

/**
 * Commands for submitting data subject requests.
 */

data class SubmitAccessRequestCommand(
    val requestedByPrincipalId: String,
    val requestedByEmail: String,
    val workspaceId: String?,
    val notes: String?,
) : CommandWithResult<DataSubjectRequestResponse>

data class SubmitExportRequestCommand(
    val requestedByPrincipalId: String,
    val requestedByEmail: String,
    val workspaceId: String?,
    val notes: String?,
) : CommandWithResult<DataSubjectRequestResponse>

data class SubmitCorrectionRequestCommand(
    val requestedByPrincipalId: String,
    val requestedByEmail: String,
    val field: CorrectionField,
    val newValue: String,
    val workspaceId: String?,
    val notes: String?,
) : CommandWithResult<DataSubjectRequestResponse>

enum class CorrectionField {
    EMAIL,
    USERNAME,
}

data class SubmitDeletionRequestCommand(
    val requestedByPrincipalId: String,
    val requestedByEmail: String,
    val workspaceId: String?,
    val notes: String?,
) : CommandWithResult<DataSubjectRequestResponse>

/**
 * Queries for checking request status and listing requests.
 */

data class CheckRequestStatusQuery(
    val requestId: String,
) : Query<DataSubjectRequestResponse?>

data class ListRequestsQuery(
    val requesterPrincipalId: String,
) : Query<List<DataSubjectRequestResponse>>
