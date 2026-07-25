package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.bus.query.QueryHandler
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.governance.domain.ConsentRecord
import com.profiletailors.smp.governance.domain.ConsentRepository
import com.profiletailors.smp.governance.domain.SubjectReference
import kotlinx.coroutines.flow.Flow

@Service
internal class RecordWorkspaceConsentHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val recordConsentHandler: RecordConsentHandler,
    private val authorizationService: GovernanceAuthorizationService,
) : CommandWithResultHandler<RecordWorkspaceConsentCommand, RecordConsentOutcome> {
    /**
     * Records consent for the current workspace.
     *
     * @param command The command containing the consent details to record.
     * @return The outcome of recording the consent.
     * @throws AuthorizationDeniedException If the caller lacks consent-write permission.
     * @throws IllegalArgumentException If no workspace is available or an enum value is invalid.
     */
    override suspend fun handle(command: RecordWorkspaceConsentCommand): RecordConsentOutcome {
        authorizationService.authorizeConsentWrite()
        return recordConsentHandler.handle(
            RecordConsentCommand(
                workspaceId = requireNotNull(resourceContextProvider.require().workspaceId),
                subjectReference = SubjectReference(command.subjectValue, command.subjectKind),
                consentType = command.consentType,
                purpose = command.purpose,
                policyVersion = command.policyVersion,
                source = command.source,
                locale = command.locale,
            ),
        )
    }
}

@Service
internal class WithdrawWorkspaceConsentHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val withdrawConsentHandler: WithdrawConsentHandler,
    private val authorizationService: GovernanceAuthorizationService,
) : CommandWithResultHandler<WithdrawWorkspaceConsentCommand, ConsentRecord> {
    /**
     * Withdraws a workspace consent for the specified subject.
     *
     * @param command The command containing the consent subject and withdrawal details.
     * @return The withdrawn consent record.
     * @throws AuthorizationDeniedException If the caller lacks consent-write permission.
     * @throws IllegalArgumentException If the workspace ID is missing or an enum value is invalid.
     */
    override suspend fun handle(command: WithdrawWorkspaceConsentCommand): ConsentRecord {
        authorizationService.authorizeConsentWrite()
        return withdrawConsentHandler.handle(
            WithdrawConsentCommand(
                workspaceId = requireNotNull(resourceContextProvider.require().workspaceId),
                subjectReference = SubjectReference(command.subjectValue, command.subjectKind),
                purpose = command.purpose,
                policyVersion = command.policyVersion,
                reason = command.reason,
            ),
        )
    }
}

@Service
internal class GetWorkspaceConsentRecordsHandler(
    private val repository: ConsentRepository,
    private val resourceContextProvider: ResourceContextProvider,
    private val authorizationService: GovernanceAuthorizationService,
) : QueryHandler<GetWorkspaceConsentRecordsQuery, Flow<ConsentRecord>> {
    /**
     * Retrieves active consent records for the current workspace.
     *
     * @param query The query containing optional subject kind and purpose filters.
     * @return The matching active consent records.
     * @throws AuthorizationDeniedException If the caller lacks consent-read permission.
     * @throws NullPointerException If no workspace ID is available.
     * @throws IllegalArgumentException If the subject kind is invalid.
     */
    override suspend fun handle(query: GetWorkspaceConsentRecordsQuery): Flow<ConsentRecord> {
        authorizationService.authorizeConsentRead()
        return repository.findActiveByWorkspace(
            requireNotNull(resourceContextProvider.require().workspaceId),
            query.subjectKind,
            query.purpose,
        )
    }
}

@Service
internal class GetConsentHistoryHandler(
    private val repository: ConsentRepository,
    private val resourceContextProvider: ResourceContextProvider,
    private val authorizationService: GovernanceAuthorizationService,
) : QueryHandler<GetConsentHistoryQuery, Flow<ConsentRecord>> {
    /**
     * Retrieves historical consent records for the current workspace identity.
     *
     * @param query The query specifying the subject and consent purpose.
     * @return The historical consent records matching the query.
     * @throws AuthorizationDeniedException If the caller lacks permission to read consent records.
     * @throws IllegalArgumentException If the workspace context has no workspace ID or the subject kind is invalid.
     */
    override suspend fun handle(query: GetConsentHistoryQuery): Flow<ConsentRecord> {
        authorizationService.authorizeConsentRead()
        return repository.findHistoricalByIdentity(
            requireNotNull(resourceContextProvider.require().workspaceId),
            SubjectReference(query.subjectValue, query.subjectKind),
            query.purpose,
        )
    }
}
