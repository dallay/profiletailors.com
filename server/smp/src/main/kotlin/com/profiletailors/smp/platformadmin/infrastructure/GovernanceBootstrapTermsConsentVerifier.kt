package com.profiletailors.smp.platformadmin.infrastructure

import com.profiletailors.smp.governance.application.CheckBootstrapTermsConsentHandler
import com.profiletailors.smp.governance.application.CheckBootstrapTermsConsentQuery
import com.profiletailors.smp.platformadmin.application.contracts.BootstrapTermsConsentVerifier
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class GovernanceBootstrapTermsConsentVerifier(
    private val checkBootstrapTermsConsentHandler: CheckBootstrapTermsConsentHandler,
) : BootstrapTermsConsentVerifier {
    override suspend fun hasCurrentTermsConsent(principalId: UUID, termsVersion: String): Boolean =
        checkBootstrapTermsConsentHandler.handle(CheckBootstrapTermsConsentQuery(principalId, termsVersion))
}
