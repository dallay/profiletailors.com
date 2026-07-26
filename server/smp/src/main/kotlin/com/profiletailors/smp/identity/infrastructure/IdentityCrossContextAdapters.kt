package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.credentials.application.RefreshSessionFailureReason
import com.profiletailors.smp.credentials.application.RefreshSessionLifecycleService
import com.profiletailors.smp.credentials.application.RefreshSessionNotActiveException
import com.profiletailors.smp.governance.application.RecordConsentCommand
import com.profiletailors.smp.governance.application.RecordConsentHandler
import com.profiletailors.smp.governance.domain.ConsentType
import com.profiletailors.smp.governance.domain.SubjectReference
import com.profiletailors.smp.identity.application.IdentityConsentRecorder
import com.profiletailors.smp.identity.application.IdentityCreatedRefreshSession
import com.profiletailors.smp.identity.application.IdentityRefreshSessionNotActiveException
import com.profiletailors.smp.identity.application.IdentityRefreshSessionPort
import com.profiletailors.smp.identity.application.IdentityRefreshSessionToken
import com.profiletailors.smp.identity.application.IdentityRotatedRefreshSession
import org.springframework.stereotype.Component

@Component
class CredentialsIdentityRefreshSessionAdapter(
    private val refreshSessionLifecycleService: RefreshSessionLifecycleService,
) : IdentityRefreshSessionPort {
    override suspend fun issue(principalId: String): IdentityCreatedRefreshSession {
        val issued = refreshSessionLifecycleService.issue(principalId)
        return IdentityCreatedRefreshSession(
            principalId = issued.principalId,
            refreshToken = IdentityRefreshSessionToken(
                lookupKey = issued.refreshToken.lookupKey,
                secret = issued.refreshToken.secret,
            ),
        )
    }

    override suspend fun rotate(rawRefreshToken: String): IdentityRotatedRefreshSession = try {
        val rotated = refreshSessionLifecycleService.rotate(rawRefreshToken)
        IdentityRotatedRefreshSession(
            principalId = rotated.current.principalId,
            refreshToken = IdentityRefreshSessionToken(
                lookupKey = rotated.current.refreshToken.lookupKey,
                secret = rotated.current.refreshToken.secret,
            ),
        )
    } catch (_: RefreshSessionNotActiveException) {
        throw IdentityRefreshSessionNotActiveException()
    }

    override suspend fun revoke(rawRefreshToken: String) {
        try {
            refreshSessionLifecycleService.revoke(rawRefreshToken)
        } catch (exception: RefreshSessionNotActiveException) {
            if (exception.reason == RefreshSessionFailureReason.MISSING) {
                throw IdentityRefreshSessionNotActiveException()
            }
            throw IdentityRefreshSessionNotActiveException()
        }
    }
}

@Component
class GovernanceIdentityConsentRecorderAdapter(private val recordConsentHandler: RecordConsentHandler) :
    IdentityConsentRecorder {
    override suspend fun recordContractAcceptance(
        workspaceId: String,
        principalId: String,
        purpose: String,
        policyVersion: String,
        source: String,
        locale: String,
    ) {
        recordConsentHandler.handle(
            RecordConsentCommand(
                workspaceId = workspaceId,
                subjectReference = SubjectReference.user(principalId),
                consentType = ConsentType.CONTRACT_ACCEPTANCE,
                purpose = purpose,
                policyVersion = policyVersion,
                source = source,
                locale = locale,
            ),
        )
    }
}
