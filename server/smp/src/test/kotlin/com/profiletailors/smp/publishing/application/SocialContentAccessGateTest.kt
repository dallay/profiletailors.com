package com.profiletailors.smp.publishing.application

import com.profiletailors.smp.publishing.domain.ActorRoleState
import com.profiletailors.smp.publishing.domain.CapabilityOperation
import com.profiletailors.smp.publishing.domain.ProviderActorId
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialContentAccessDenial
import com.profiletailors.smp.publishing.domain.SocialContentAccessDeniedException
import com.profiletailors.smp.publishing.domain.SocialContentAccessRequest
import com.profiletailors.smp.publishing.domain.SocialContentActor
import com.profiletailors.smp.publishing.domain.SocialContentApprovalEvidence
import com.profiletailors.smp.publishing.domain.SocialContentApprovalEvidenceRepository
import com.profiletailors.smp.publishing.domain.SocialContentFeatureGates
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.domain.WorkspaceScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SocialContentAccessGateTest {
    private val scope = WorkspaceScope("workspace-1")
    private val actor = SocialContentActor(
        id = "actor-1",
        socialAccountId = "account-1",
        scope = scope,
        connectionId = "connection-1",
        provider = SocialProvider.LINKEDIN,
        externalActorId = ProviderActorId("urn:li:organization:123"),
        kind = SocialAccountKind.ORGANIZATION_PAGE,
        displayName = "Profile Tailors",
        roleState = ActorRoleState.ADMIN,
        grantedScopes = REQUIRED_SCOPES,
    )
    private val evidence = SocialContentApprovalEvidence(
        workspaceId = scope.value,
        socialAccountId = actor.socialAccountId,
        roleState = ActorRoleState.ADMIN,
        grantedScopes = REQUIRED_SCOPES,
        communityManagementApproved = true,
        apiVersion = "202606",
        retentionPolicyVersion = "social-content-v1",
    )

    @Test
    fun `disabled operation is denied by default`() = runTest {
        assertDenial(
            policy = SocialContentAccessPolicy(
                featureGates = SocialContentFeatureGates(),
                retentionPolicyVersion = evidence.retentionPolicyVersion,
            ),
            expected = SocialContentAccessDenial.OPERATION_DISABLED,
        )
    }

    @Test
    fun `missing approval evidence is denied`() = runTest {
        assertDenial(
            evidence = null,
            expected = SocialContentAccessDenial.EVIDENCE_MISSING,
        )
    }

    @Test
    fun `inactive community management approval is denied`() = runTest {
        assertDenial(
            evidence = evidence.copy(communityManagementApproved = false),
            expected = SocialContentAccessDenial.COMMUNITY_MANAGEMENT_NOT_APPROVED,
        )
    }

    @Test
    fun `workspace mismatch is denied`() = runTest {
        assertDenial(
            evidence = evidence.copy(workspaceId = "workspace-2"),
            expected = SocialContentAccessDenial.WORKSPACE_MISMATCH,
        )
    }

    @Test
    fun `social account mismatch is denied`() = runTest {
        assertDenial(
            evidence = evidence.copy(socialAccountId = "account-2"),
            expected = SocialContentAccessDenial.ACCOUNT_MISMATCH,
        )
    }

    @Test
    fun `personal account kind is denied`() = runTest {
        assertDenial(
            request = request(actorKind = SocialAccountKind.PERSONAL_PROFILE),
            expected = SocialContentAccessDenial.ORGANIZATION_PAGE_REQUIRED,
        )
    }

    @Test
    fun `non administrator actor is denied`() = runTest {
        assertDenial(
            request = request(roleState = ActorRoleState.MEMBER),
            expected = SocialContentAccessDenial.ADMIN_ROLE_REQUIRED,
        )
    }

    @Test
    fun `missing organization read scope is denied`() = runTest {
        assertDenial(
            request = request(grantedScopes = setOf("r_organization_social")),
            expected = SocialContentAccessDenial.REQUIRED_SCOPE_MISSING,
        )
    }

    @Test
    fun `blank API version is denied`() = runTest {
        assertDenial(
            request = request(apiVersion = " "),
            expected = SocialContentAccessDenial.API_VERSION_REQUIRED,
        )
    }

    @Test
    fun `API version outside explicit allowlist is denied`() = runTest {
        assertDenial(
            request = request(apiVersion = "202701"),
            expected = SocialContentAccessDenial.API_VERSION_UNSUPPORTED,
        )
    }

    @Test
    fun `blank retention policy version is denied`() = runTest {
        assertDenial(
            evidence = evidence.copy(retentionPolicyVersion = ""),
            expected = SocialContentAccessDenial.RETENTION_POLICY_VERSION_REQUIRED,
        )
    }

    @Test
    fun `valid complete evidence is allowed`() = runTest {
        val gate = gate(evidence = evidence)

        gate.authorize(request())
    }

    private suspend fun assertDenial(
        request: SocialContentAccessRequest = request(),
        evidence: SocialContentApprovalEvidence? = this.evidence,
        policy: SocialContentAccessPolicy = SocialContentAccessPolicy(
            featureGates = SocialContentFeatureGates(importEnabled = true),
            retentionPolicyVersion = this.evidence.retentionPolicyVersion,
        ),
        expected: SocialContentAccessDenial,
    ) {
        val exception = assertThrows<SocialContentAccessDeniedException> {
            gate(evidence = evidence, policy = policy).authorize(request)
        }

        assertEquals(expected, exception.denial)
    }

    private fun request(
        actorKind: SocialAccountKind = actor.kind,
        roleState: ActorRoleState? = actor.roleState,
        grantedScopes: Set<String>? = actor.grantedScopes,
        apiVersion: String? = evidence.apiVersion,
    ) = SocialContentAccessRequest(
        scope = scope,
        socialAccountId = actor.socialAccountId,
        operation = CapabilityOperation.READ_POSTS,
        actorKind = actorKind,
        roleState = roleState,
        grantedScopes = grantedScopes,
        apiVersion = apiVersion,
    )

    private fun gate(
        evidence: SocialContentApprovalEvidence?,
        policy: SocialContentAccessPolicy = SocialContentAccessPolicy(
            featureGates = SocialContentFeatureGates(importEnabled = true),
            retentionPolicyVersion = this.evidence.retentionPolicyVersion,
        ),
    ) = DefaultSocialContentAccessGate(
        approvalEvidenceRepository = InMemoryApprovalEvidenceRepository(evidence),
        policy = policy,
    )

    private class InMemoryApprovalEvidenceRepository(private val evidence: SocialContentApprovalEvidence?) :
        SocialContentApprovalEvidenceRepository {
        override suspend fun findByWorkspaceAndAccount(
            workspaceId: String,
            socialAccountId: String,
        ): SocialContentApprovalEvidence? = evidence
    }

    private companion object {
        val REQUIRED_SCOPES = setOf(
            "r_organization_social",
            "r_organization_social_feed",
        )
    }
}
