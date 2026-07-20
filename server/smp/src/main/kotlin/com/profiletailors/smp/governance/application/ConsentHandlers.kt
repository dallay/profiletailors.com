package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.bus.query.QueryHandler
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.authorization.domain.AuthorizationDeniedException
import com.profiletailors.smp.authorization.domain.PermissionKey
import com.profiletailors.smp.authorization.domain.WorkspaceAuthorizationDecider
import com.profiletailors.smp.governance.domain.ConsentRecord
import com.profiletailors.smp.governance.domain.ConsentRepository
import com.profiletailors.smp.governance.domain.ConsentType
import com.profiletailors.smp.governance.domain.SubjectKind
import com.profiletailors.smp.governance.domain.SubjectReference
import kotlinx.coroutines.flow.toList

private val CONSENT_READ_PERMISSION = PermissionKey.of("workspace", "consent", "read")

@Service
internal class RecordWorkspaceConsentHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val recordConsentHandler: RecordConsentHandler,
) : CommandWithResultHandler<RecordWorkspaceConsentCommand, RecordConsentOutcome> {
    override suspend fun handle(command: RecordWorkspaceConsentCommand): RecordConsentOutcome =
        recordConsentHandler.handle(
            RecordConsentCommand(
                workspaceId = requireNotNull(resourceContextProvider.require().workspaceId),
                subjectReference = SubjectReference(command.subjectValue, SubjectKind.valueOf(command.subjectKind)),
                consentType = ConsentType.valueOf(command.consentType),
                purpose = command.purpose,
                policyVersion = command.policyVersion,
                source = command.source,
                locale = command.locale,
            ),
        )
}

@Service
internal class WithdrawWorkspaceConsentHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val withdrawConsentHandler: WithdrawConsentHandler,
) : CommandWithResultHandler<WithdrawWorkspaceConsentCommand, ConsentRecord> {
    override suspend fun handle(command: WithdrawWorkspaceConsentCommand): ConsentRecord =
        withdrawConsentHandler.handle(
            WithdrawConsentCommand(
                workspaceId = requireNotNull(resourceContextProvider.require().workspaceId),
                subjectReference = SubjectReference(command.subjectValue, SubjectKind.valueOf(command.subjectKind)),
                purpose = command.purpose,
                policyVersion = command.policyVersion,
                reason = command.reason,
            ),
        )
}

@Service
internal class GetWorkspaceConsentRecordsHandler(
    private val repository: ConsentRepository,
    private val resourceContextProvider: ResourceContextProvider,
    private val authorizationDecider: WorkspaceAuthorizationDecider,
) : QueryHandler<GetWorkspaceConsentRecordsQuery, List<ConsentRecord>> {
    override suspend fun handle(query: GetWorkspaceConsentRecordsQuery): List<ConsentRecord> {
        authorize()
        return repository.findActiveByWorkspace(
            requireNotNull(resourceContextProvider.require().workspaceId),
            query.subjectKind?.let(SubjectKind::valueOf),
            query.purpose,
        ).toList()
    }

    private suspend fun authorize() {
        val decision = authorizationDecider.decideDetailed(CONSENT_READ_PERMISSION)
        if (decision.decision != AuthorizationDecision.ALLOW) {
            throw AuthorizationDeniedException.forDecision(decision, CONSENT_READ_PERMISSION)
        }
    }
}

@Service
internal class GetConsentHistoryHandler(
    private val repository: ConsentRepository,
    private val resourceContextProvider: ResourceContextProvider,
    private val authorizationDecider: WorkspaceAuthorizationDecider,
) : QueryHandler<GetConsentHistoryQuery, List<ConsentRecord>> {
    override suspend fun handle(query: GetConsentHistoryQuery): List<ConsentRecord> {
        val decision = authorizationDecider.decideDetailed(CONSENT_READ_PERMISSION)
        if (decision.decision != AuthorizationDecision.ALLOW) {
            throw AuthorizationDeniedException.forDecision(decision, CONSENT_READ_PERMISSION)
        }
        return repository.findHistoricalByIdentity(
            requireNotNull(resourceContextProvider.require().workspaceId),
            SubjectReference(query.subjectValue, SubjectKind.valueOf(query.subjectKind)),
            query.purpose,
        ).toList()
    }
}
