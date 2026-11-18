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

    suspend fun create(issuer: String, subject: String, principalId: String)

    suspend fun synchronizeVerifiedEmail(principalId: String, email: String): VerifiedEmailSyncResult
}

class ExternalIdentityLinkConflictException(cause: Throwable? = null) :
    RuntimeException("Identity link already exists.", cause)

class ExternalIdentityLinkTargetNotFoundException : RuntimeException("Target principal was not found.")

class ExternalIdentityLinkProvisioningService(
    private val externalIdentityLinkGateway: ExternalIdentityLinkGateway,
    private val principalIdentityLookup: PrincipalIdentityLookup,
    private val expectedIssuer: String,
) {
    suspend fun provision(principalId: String, issuer: String, subject: String) {
        require(principalId.isNotBlank())
        require(issuer == expectedIssuer)
        require(subject.isNotBlank() && subject.length <= MAX_SUBJECT_LENGTH)
        if (principalIdentityLookup.findByPrincipalId(principalId) == null) {
            throw ExternalIdentityLinkTargetNotFoundException()
        }
        externalIdentityLinkGateway.create(issuer, subject, principalId)
    }

    private companion object {
        const val MAX_SUBJECT_LENGTH = 255
    }
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
                it.principalType == PrincipalType.USER &&
                    it.status == PrincipalStatus.ACTIVE &&
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
