package com.profiletailors.smp.identity.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.identity.domain.ExternalIdentityLink
import com.profiletailors.smp.identity.domain.ExternalIdentityLinkStatus
import com.profiletailors.smp.identity.domain.PrincipalIdentityFacts
import com.profiletailors.smp.identity.domain.PrincipalStatus
import com.profiletailors.smp.identity.domain.UserAccountState

data class ValidatedExternalIdentity(
    val issuer: String,
    val subject: String,
    val email: String?,
    val emailVerified: Boolean,
)

enum class VerifiedEmailSyncResult {
    UPDATED,
    CONFLICT,
    UNCHANGED,
}

data class ResolvedExternalIdentity(
    val principal: PrincipalIdentityFacts,
    val emailSyncResult: VerifiedEmailSyncResult?,
)

interface ExternalIdentityLinkGateway {
    suspend fun findByIssuerAndSubject(issuer: String, subject: String): List<ExternalIdentityLink>

    suspend fun synchronizeVerifiedEmail(principalId: String, email: String): VerifiedEmailSyncResult
}

@Service
class ExternalIdentityResolutionService(
    private val externalIdentityLinkGateway: ExternalIdentityLinkGateway,
    private val principalIdentityLookup: PrincipalIdentityLookup,
) {
    suspend fun resolve(identity: ValidatedExternalIdentity): ResolvedExternalIdentity? {
        if (identity.issuer.isBlank() || identity.subject.isBlank()) return null

        val link = externalIdentityLinkGateway.findByIssuerAndSubject(identity.issuer, identity.subject)
            .singleOrNull()
            ?.takeIf { it.status == ExternalIdentityLinkStatus.APPROVED }
            ?: return null

        val principal = principalIdentityLookup.findByPrincipalId(link.principalId)
            ?.takeIf {
                it.principalType == PrincipalType.USER && it.status == PrincipalStatus.ACTIVE &&
                    it.accountState == UserAccountState.ACTIVE
            }
            ?: return null

        val emailSyncResult = identity.email
            ?.takeIf { identity.emailVerified && it.isNotBlank() }
            ?.let { email ->
                externalIdentityLinkGateway.synchronizeVerifiedEmail(principal.principalId, email)
            }

        return ResolvedExternalIdentity(principal, emailSyncResult)
    }
}
