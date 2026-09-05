package com.profiletailors.smp.privacy.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.bus.query.QueryHandler
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.privacy.domain.CreateDataSubjectRequest
import com.profiletailors.smp.privacy.domain.DataSubjectRequest
import com.profiletailors.smp.privacy.domain.DataSubjectRequestId
import com.profiletailors.smp.privacy.domain.DataSubjectRequestRepository
import com.profiletailors.smp.privacy.domain.DataSubjectRequestStatus
import com.profiletailors.smp.privacy.domain.RequestType
import java.time.Clock

private const val ACTION_DSAR_SUBMITTED = "dsar.submitted"
private const val ACTION_DSAR_COMPLETED = "dsar.completed"

/**
 * Handler for [SubmitAccessRequestCommand].
 *
 * Creates an ACCESS request, aggregates the user's data across all
 * contexts, stores the result, and transitions to COMPLETED synchronously.
 *
 * NOTE: `@Service` is intentionally omitted until all implementations
 * (`IdentityData`, `CredentialsData`, etc.) are available in the
 * infrastructure layer. See `PrivacyContracts.kt`.
 */
internal class SubmitAccessRequestHandler(
    private val repository: DataSubjectRequestRepository,
    private val dataAggregationService: DataAggregationService,
    private val auditor: PrivacyMutationAuditor,
    private val clock: Clock = Clock.systemUTC(),
    private val serializer: PrivacyDataSerializer = PrivacyDataSerializer { data -> data.toString() },
) : CommandWithResultHandler<SubmitAccessRequestCommand, DataSubjectRequestResponse> {

    override suspend fun handle(command: SubmitAccessRequestCommand): DataSubjectRequestResponse {
        val now = clock.instant()
        val request = DataSubjectRequest.create(
            CreateDataSubjectRequest(
                id = DataSubjectRequestId.random(),
                requestType = RequestType.ACCESS,
                requestedBy = command.requestedByPrincipalId,
                requestedByEmail = command.requestedByEmail,
                workspaceId = command.workspaceId,
                notes = command.notes,
                createdAt = now,
            ),
        )

        auditor.recordSuccess(
            action = ACTION_DSAR_SUBMITTED,
            requestId = request.id.value,
            details = mapOf("type" to "ACCESS"),
        )

        val aggregatedData = dataAggregationService.aggregate(
            principalId = command.requestedByPrincipalId,
            email = command.requestedByEmail,
        )

        val resultJson = serializer.toJson(aggregatedData)
        val completedRequest = request.transitionTo(
            target = DataSubjectRequestStatus.COMPLETED,
            completedAt = now,
        ).copy(resultRef = resultJson)

        repository.save(completedRequest)

        auditor.recordSuccess(
            action = ACTION_DSAR_COMPLETED,
            requestId = request.id.value,
            details = mapOf("type" to "ACCESS"),
        )

        return completedRequest.toResponse()
    }
}

/**
 * Handler for [SubmitExportRequestCommand].
 *
 * Creates an EXPORT request, aggregates the user's data, generates
 * a JSON export, uploads it, and transitions to COMPLETED with
 * a download URL.
 *
 * NOTE: `@Service` intentionally omitted — depends on `DataAggregationService`
 * and `Storage`, which are not yet Spring beans.
 */
internal class SubmitExportRequestHandler(
    private val repository: DataSubjectRequestRepository,
    private val dataAggregationService: DataAggregationService,
    private val storage: Storage,
    private val auditor: PrivacyMutationAuditor,
    private val clock: Clock = Clock.systemUTC(),
    private val serializer: PrivacyDataSerializer = PrivacyDataSerializer { data -> data.toString() },
) : CommandWithResultHandler<SubmitExportRequestCommand, DataSubjectRequestResponse> {

    override suspend fun handle(command: SubmitExportRequestCommand): DataSubjectRequestResponse {
        val now = clock.instant()
        val request = DataSubjectRequest.create(
            CreateDataSubjectRequest(
                id = DataSubjectRequestId.random(),
                requestType = RequestType.EXPORT,
                requestedBy = command.requestedByPrincipalId,
                requestedByEmail = command.requestedByEmail,
                workspaceId = command.workspaceId,
                notes = command.notes,
                createdAt = now,
            ),
        )

        auditor.recordSuccess(
            action = ACTION_DSAR_SUBMITTED,
            requestId = request.id.value,
            details = mapOf("type" to "EXPORT"),
        )

        val aggregatedData = dataAggregationService.aggregate(
            principalId = command.requestedByPrincipalId,
            email = command.requestedByEmail,
        )

        val jsonContent = serializer.toJson(aggregatedData)
        val exportKey = "dsar-exports/${request.id.value}.json"
        val downloadUrl = storage.uploadJson(exportKey, jsonContent)

        val completedRequest = request.transitionTo(
            target = DataSubjectRequestStatus.COMPLETED,
            completedAt = now,
        ).copy(resultRef = downloadUrl)

        repository.save(completedRequest)

        auditor.recordSuccess(
            action = ACTION_DSAR_COMPLETED,
            requestId = request.id.value,
            details = mapOf("type" to "EXPORT"),
        )

        return completedRequest.toResponse()
    }
}

/**
 * Handler for [SubmitCorrectionRequestCommand].
 *
 * Validates the correction request via [AnonymizationService], applies
 * the identity field change, propagates to waitlist entries on email
 * change, then records the request as COMPLETED.
 *
 * NOTE: `@Service` intentionally omitted — depends on `AnonymizationService`
 * which is not yet a Spring bean.
 */
internal class SubmitCorrectionRequestHandler(
    private val repository: DataSubjectRequestRepository,
    private val anonymizationService: AnonymizationService,
    private val auditor: PrivacyMutationAuditor,
    private val clock: Clock = Clock.systemUTC(),
    private val serializer: PrivacyDataSerializer = PrivacyDataSerializer { data -> data.toString() },
) : CommandWithResultHandler<SubmitCorrectionRequestCommand, DataSubjectRequestResponse> {

    override suspend fun handle(command: SubmitCorrectionRequestCommand): DataSubjectRequestResponse {
        // Validate and apply the identity correction
        val correctionResult = anonymizationService.verifyCorrection(
            principalId = command.requestedByPrincipalId,
            field = command.field,
            newValue = command.newValue,
        )

        require(correctionResult is AnonymizationService.CorrectionResult.Success) {
            "Principal ${command.requestedByPrincipalId} not found for correction"
        }

        // Propagate email changes to waitlist entries
        if (command.field == CorrectionField.EMAIL) {
            anonymizationService.anonymizeWaitlistByEmail(command.requestedByEmail, clock.instant())
        }

        val now = clock.instant()
        val correctionData = serializer.toJson(
            mapOf(
                "field" to command.field.name.lowercase(),
                "newValue" to command.newValue,
            ),
        )

        val request = DataSubjectRequest.create(
            CreateDataSubjectRequest(
                id = DataSubjectRequestId.random(),
                requestType = RequestType.CORRECTION,
                requestedBy = command.requestedByPrincipalId,
                requestedByEmail = command.requestedByEmail,
                workspaceId = command.workspaceId,
                notes = command.notes,
                correctionData = correctionData,
                createdAt = now,
            ),
        )

        auditor.recordSuccess(
            action = ACTION_DSAR_SUBMITTED,
            requestId = request.id.value,
            details = mapOf("type" to "CORRECTION", "field" to command.field.name.lowercase()),
        )

        val completedRequest = request.transitionTo(
            target = DataSubjectRequestStatus.COMPLETED,
            completedAt = now,
        )
        repository.save(completedRequest)

        auditor.recordSuccess(
            action = ACTION_DSAR_COMPLETED,
            requestId = request.id.value,
            details = mapOf("type" to "CORRECTION", "field" to command.field.name.lowercase()),
        )

        return completedRequest.toResponse()
    }
}

/**
 * Handler for [SubmitDeletionRequestCommand].
 *
 * 3-phase deletion orchestration:
 * 1. Validate not sole-owner, then anonymize PII inside [AtomicTransactionRunner]
 * 2. Revoke sessions + remove memberships
 * 3. Mark media for garbage collection
 *
 * NOTE: `@Service` intentionally omitted — depends on `AnonymizationService`
 * and multiple ports (`TenancyData`, `PublishingDeletion`, etc.)
 * that are not yet implemented in the infrastructure layer.
 */
internal class SubmitDeletionRequestHandler(
    private val repository: DataSubjectRequestRepository,
    private val anonymizationService: AnonymizationService,
    private val tenancyData: TenancyData,
    private val publishing: PublishingDeletion,
    private val transactionRunner: AtomicTransactionRunner,
    private val auditor: PrivacyMutationAuditor,
    private val clock: Clock = Clock.systemUTC(),
) : CommandWithResultHandler<SubmitDeletionRequestCommand, DataSubjectRequestResponse> {

    override suspend fun handle(command: SubmitDeletionRequestCommand): DataSubjectRequestResponse {
        // Fail-fast: prevent deletion if user is sole owner in any workspace
        val isSoleOwner = tenancyData.isSoleOwnerInAnyWorkspace(command.requestedByPrincipalId)
        require(!isSoleOwner) {
            "Cannot delete principal ${command.requestedByPrincipalId}: is sole owner in one or more workspaces"
        }

        // Phase 1: Anonymize PII inside atomic transaction (fail-fast, audited)
        transactionRunner.runAtomically {
            anonymizationService.anonymizeIdentityAndWaitlist(
                principalId = command.requestedByPrincipalId,
                email = command.requestedByEmail,
                timestamp = clock.instant(),
            )
        }

        // Phase 2: Capture workspace IDs before revoking memberships
        val workspaceIds = tenancyData.getMembershipWorkspaceIds(command.requestedByPrincipalId)

        // Phase 3: Revoke credentials and memberships (best-effort)
        anonymizationService.revokeCredentials(command.requestedByPrincipalId)
        publishing.deleteSocialConnections(command.requestedByPrincipalId)
        publishing.deleteSecureCredentials(command.requestedByPrincipalId)
        publishing.cancelPendingPublications(command.requestedByPrincipalId)
        tenancyData.removeAllMemberships(command.requestedByPrincipalId)

        // Phase 4: Mark media for GC
        if (workspaceIds.isNotEmpty()) {
            anonymizationService.markMediaForGc(command.requestedByPrincipalId, workspaceIds)
        }

        // Record the deletion request
        val now = clock.instant()
        val request = DataSubjectRequest.create(
            CreateDataSubjectRequest(
                id = DataSubjectRequestId.random(),
                requestType = RequestType.DELETION,
                requestedBy = command.requestedByPrincipalId,
                requestedByEmail = command.requestedByEmail,
                workspaceId = command.workspaceId,
                notes = command.notes,
                createdAt = now,
            ),
        )

        auditor.recordSuccess(
            action = ACTION_DSAR_SUBMITTED,
            requestId = request.id.value,
            details = mapOf("type" to "DELETION"),
        )

        val completedRequest = request.transitionTo(
            target = DataSubjectRequestStatus.COMPLETED,
            completedAt = now,
        )
        repository.save(completedRequest)

        auditor.recordSuccess(
            action = ACTION_DSAR_COMPLETED,
            requestId = request.id.value,
            details = mapOf("type" to "DELETION"),
        )

        return completedRequest.toResponse()
    }
}

/**
 * Handler for [CheckRequestStatusQuery].
 *
 * Returns the current state of a single request by ID, or null if not found.
 */
@Service
internal class CheckRequestStatusHandler(private val repository: DataSubjectRequestRepository) :
    QueryHandler<CheckRequestStatusQuery, DataSubjectRequestResponse?> {

    override suspend fun handle(query: CheckRequestStatusQuery): DataSubjectRequestResponse? =
        repository.findById(query.requestId)?.toResponse()
}

/**
 * Handler for [ListRequestsQuery].
 *
 * Returns all requests for a given principal, ordered by most recent first.
 */
@Service
internal class ListRequestsHandler(private val repository: DataSubjectRequestRepository) :
    QueryHandler<ListRequestsQuery, List<DataSubjectRequestResponse>> {

    override suspend fun handle(query: ListRequestsQuery): List<DataSubjectRequestResponse> =
        repository.findByRequester(query.requesterPrincipalId)
            .map { it.toResponse() }
            .sortedByDescending { it.createdAt }
}

// ——————— Response mapping ———————

internal fun DataSubjectRequest.toResponse(): DataSubjectRequestResponse = DataSubjectRequestResponse(
    id = id.value,
    type = requestType.name,
    status = status.name,
    requestedBy = requestedBy,
    requestedByEmail = requestedByEmail,
    workspaceId = workspaceId,
    resultRef = resultRef,
    rejectionReason = rejectionReason,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
)
