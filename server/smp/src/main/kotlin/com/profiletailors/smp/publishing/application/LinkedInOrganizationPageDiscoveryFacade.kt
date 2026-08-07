package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.publishing.domain.SocialConnectionRepository
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialContentActor
import com.profiletailors.smp.publishing.domain.SocialContentApprovalEvidence
import com.profiletailors.smp.publishing.domain.SocialContentApprovalEvidenceRepository
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.domain.WorkspaceScope
import com.profiletailors.smp.tenancy.application.requireWorkspaceContext

class LinkedInOrganizationPageDiscoveryFacade(
    private val resourceContextProvider: ResourceContextProvider,
    private val connectionRepository: SocialConnectionRepository,
    private val accountRepository: SocialAccountRepository,
    private val approvalEvidenceRepository: SocialContentApprovalEvidenceRepository,
    private val discoveryHandler: LinkedInPageDiscoveryHandler,
) {
    suspend fun discover(socialAccountId: String): List<SocialContentActor> {
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        val account = accountRepository.findByWorkspaceAndId(workspaceId, socialAccountId)
            ?: throw LinkedInOrganizationPageDiscoveryException(
                LinkedInOrganizationPageDiscoveryFailure.ACCOUNT_NOT_FOUND,
            )
        requireAccount(
            account.provider == SocialProvider.LINKEDIN,
            LinkedInOrganizationPageDiscoveryFailure.NON_LINKEDIN_ACCOUNT,
        )
        requireAccount(
            account.status == SocialConnectionStatus.ACTIVE,
            LinkedInOrganizationPageDiscoveryFailure.INACTIVE_ACCOUNT,
        )
        requireAccount(
            account.kind == SocialAccountKind.ORGANIZATION_PAGE,
            LinkedInOrganizationPageDiscoveryFailure.PERSONAL_PROFILE_ACCOUNT,
        )

        val connection = connectionRepository.findByWorkspaceAndId(workspaceId, account.socialConnectionId)
            ?: throw LinkedInOrganizationPageDiscoveryException(
                LinkedInOrganizationPageDiscoveryFailure.CONNECTION_NOT_FOUND,
            )
        requireConnection(
            connection.status == SocialConnectionStatus.ACTIVE,
            LinkedInOrganizationPageDiscoveryFailure.INACTIVE_CONNECTION,
        )
        requireConnection(
            connection.provider == SocialProvider.LINKEDIN,
            LinkedInOrganizationPageDiscoveryFailure.NON_LINKEDIN_CONNECTION,
        )

        val evidence = approvalEvidenceRepository.findByWorkspaceAndAccount(workspaceId, account.id)
            ?: throw LinkedInOrganizationPageDiscoveryException(
                LinkedInOrganizationPageDiscoveryFailure.MISSING_APPROVAL,
            )
        validateEvidence(evidence, workspaceId, account.id)

        return discoveryHandler.handle(
            scope = WorkspaceScope(workspaceId),
            connectionId = connection.id,
            provider = SocialProvider.LINKEDIN,
        )
    }

    private fun validateEvidence(
        evidence: SocialContentApprovalEvidence,
        workspaceId: String,
        socialAccountId: String,
    ) {
        val missingScope = REQUIRED_ORGANIZATION_READ_SCOPES.firstOrNull { it !in evidence.grantedScopes }
        val failure = when {
            evidence.workspaceId != workspaceId || evidence.socialAccountId != socialAccountId ->
                LinkedInOrganizationPageDiscoveryFailure.INVALID_APPROVAL_EVIDENCE
            !evidence.communityManagementApproved -> LinkedInOrganizationPageDiscoveryFailure.MISSING_APPROVAL
            evidence.roleState != com.profiletailors.smp.publishing.domain.ActorRoleState.ADMIN ->
                LinkedInOrganizationPageDiscoveryFailure.ADMIN_ROLE_REQUIRED
            missingScope != null -> LinkedInOrganizationPageDiscoveryFailure.REQUIRED_SCOPE_MISSING
            evidence.apiVersion.isBlank() -> LinkedInOrganizationPageDiscoveryFailure.API_VERSION_MISSING
            evidence.retentionPolicyVersion.isBlank() ->
                LinkedInOrganizationPageDiscoveryFailure.RETENTION_POLICY_VERSION_MISSING
            else -> null
        }
        failure?.let { throw LinkedInOrganizationPageDiscoveryException(it) }
    }

    private fun requireAccount(condition: Boolean, failure: LinkedInOrganizationPageDiscoveryFailure) {
        if (!condition) throw LinkedInOrganizationPageDiscoveryException(failure)
    }

    private fun requireConnection(condition: Boolean, failure: LinkedInOrganizationPageDiscoveryFailure) {
        if (!condition) throw LinkedInOrganizationPageDiscoveryException(failure)
    }

    private companion object {
        val REQUIRED_ORGANIZATION_READ_SCOPES = setOf(
            "r_organization_social",
            "r_organization_social_feed",
        )
    }
}

enum class LinkedInOrganizationPageDiscoveryFailure {
    ACCOUNT_NOT_FOUND,
    NON_LINKEDIN_ACCOUNT,
    INACTIVE_ACCOUNT,
    PERSONAL_PROFILE_ACCOUNT,
    CONNECTION_NOT_FOUND,
    INACTIVE_CONNECTION,
    NON_LINKEDIN_CONNECTION,
    MISSING_APPROVAL,
    INVALID_APPROVAL_EVIDENCE,
    ADMIN_ROLE_REQUIRED,
    REQUIRED_SCOPE_MISSING,
    API_VERSION_MISSING,
    RETENTION_POLICY_VERSION_MISSING,
}

class LinkedInOrganizationPageDiscoveryException(val failure: LinkedInOrganizationPageDiscoveryFailure) :
    IllegalStateException("LinkedIn organization Page discovery denied: $failure")
