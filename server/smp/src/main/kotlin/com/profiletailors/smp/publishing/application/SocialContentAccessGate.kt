package com.profiletailors.smp.publishing.application

import com.profiletailors.smp.publishing.domain.ActorRoleState
import com.profiletailors.smp.publishing.domain.CapabilityOperation
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialContentAccessDenial
import com.profiletailors.smp.publishing.domain.SocialContentAccessDeniedException
import com.profiletailors.smp.publishing.domain.SocialContentAccessRequest
import com.profiletailors.smp.publishing.domain.SocialContentApprovalEvidence
import com.profiletailors.smp.publishing.domain.SocialContentApprovalEvidenceRepository
import com.profiletailors.smp.publishing.domain.SocialContentFeatureGates

interface SocialContentAccessGate {
    suspend fun authorize(request: SocialContentAccessRequest)
}

data class SocialContentAccessPolicy(
    val featureGates: SocialContentFeatureGates = SocialContentFeatureGates(),
    val supportedApiVersions: Set<String> = setOf("202606"),
    val retentionPolicyVersion: String = "",
)

class DefaultSocialContentAccessGate(
    private val approvalEvidenceRepository: SocialContentApprovalEvidenceRepository,
    private val policy: SocialContentAccessPolicy,
) : SocialContentAccessGate {
    override suspend fun authorize(request: SocialContentAccessRequest) {
        if (!policy.featureGates.isEnabled(request.operation)) {
            deny(SocialContentAccessDenial.OPERATION_DISABLED)
        }
        val evidence = approvalEvidenceRepository.findByWorkspaceAndAccount(
            workspaceId = request.scope.value,
            socialAccountId = request.socialAccountId,
        ) ?: deny(SocialContentAccessDenial.EVIDENCE_MISSING)
        validate(request, evidence)
    }

    private fun validate(request: SocialContentAccessRequest, evidence: SocialContentApprovalEvidence) {
        validateIdentity(request, evidence)
        validateApproval(evidence)
        validateActor(request, evidence)
        validateScopes(request, evidence)
        validateApiVersion(request, evidence)
        validateRetention(evidence)
    }

    private fun validateIdentity(request: SocialContentAccessRequest, evidence: SocialContentApprovalEvidence) {
        when {
            evidence.workspaceId != request.scope.value -> deny(SocialContentAccessDenial.WORKSPACE_MISMATCH)
            evidence.socialAccountId != request.socialAccountId -> deny(SocialContentAccessDenial.ACCOUNT_MISMATCH)
        }
    }

    private fun validateApproval(evidence: SocialContentApprovalEvidence) {
        if (!evidence.communityManagementApproved) {
            deny(SocialContentAccessDenial.COMMUNITY_MANAGEMENT_NOT_APPROVED)
        }
    }

    private fun validateActor(request: SocialContentAccessRequest, evidence: SocialContentApprovalEvidence) {
        when {
            request.actorKind != SocialAccountKind.ORGANIZATION_PAGE ->
                deny(SocialContentAccessDenial.ORGANIZATION_PAGE_REQUIRED)
            request.roleState != ActorRoleState.ADMIN || evidence.roleState != ActorRoleState.ADMIN ->
                deny(SocialContentAccessDenial.ADMIN_ROLE_REQUIRED)
        }
    }

    private fun validateScopes(request: SocialContentAccessRequest, evidence: SocialContentApprovalEvidence) {
        when {
            request.grantedScopes == null || !REQUIRED_ORGANIZATION_READ_SCOPES.all { it in request.grantedScopes } ->
                deny(SocialContentAccessDenial.REQUIRED_SCOPE_MISSING)
            !REQUIRED_ORGANIZATION_READ_SCOPES.all { it in evidence.grantedScopes } ->
                deny(SocialContentAccessDenial.REQUIRED_SCOPE_MISSING)
        }
    }

    private fun validateApiVersion(request: SocialContentAccessRequest, evidence: SocialContentApprovalEvidence) {
        when {
            request.apiVersion.isNullOrBlank() || evidence.apiVersion.isBlank() ->
                deny(SocialContentAccessDenial.API_VERSION_REQUIRED)
            request.apiVersion !in policy.supportedApiVersions || evidence.apiVersion !in policy.supportedApiVersions ->
                deny(SocialContentAccessDenial.API_VERSION_UNSUPPORTED)
        }
    }

    private fun validateRetention(evidence: SocialContentApprovalEvidence) {
        if (policy.retentionPolicyVersion.isBlank() || evidence.retentionPolicyVersion.isBlank()) {
            deny(SocialContentAccessDenial.RETENTION_POLICY_VERSION_REQUIRED)
        }
        if (policy.retentionPolicyVersion != evidence.retentionPolicyVersion) {
            deny(SocialContentAccessDenial.RETENTION_POLICY_VERSION_REQUIRED)
        }
    }

    private fun deny(denial: SocialContentAccessDenial): Nothing = throw SocialContentAccessDeniedException(denial)

    private fun SocialContentFeatureGates.isEnabled(operation: CapabilityOperation): Boolean = when (operation) {
        CapabilityOperation.DISCOVER_ACTORS -> discoveryEnabled
        CapabilityOperation.READ_POSTS -> importEnabled
        CapabilityOperation.READ_COMMENTS -> inboxEnabled
        CapabilityOperation.REPLY -> repliesEnabled
    }

    private companion object {
        val REQUIRED_ORGANIZATION_READ_SCOPES = setOf(
            "r_organization_social",
            "r_organization_social_feed",
        )
    }
}
