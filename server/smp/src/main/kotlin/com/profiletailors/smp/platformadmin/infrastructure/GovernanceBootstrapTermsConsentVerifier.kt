package com.profiletailors.smp.platformadmin.infrastructure

import com.profiletailors.smp.governance.domain.ConsentRepository
import com.profiletailors.smp.governance.domain.ConsentScope
import com.profiletailors.smp.governance.domain.SubjectReference
import com.profiletailors.smp.platformadmin.application.contracts.BootstrapTermsConsentVerifier
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class GovernanceBootstrapTermsConsentVerifier(private val consentRepository: ConsentRepository) :
    BootstrapTermsConsentVerifier {
    override suspend fun hasCurrentTermsConsent(principalId: UUID, termsVersion: String): Boolean =
        consentRepository.existsActive(
            scope = ConsentScope.Platform,
            subjectReference = SubjectReference.user(principalId.toString()),
            purpose = TERMS_ACCEPTANCE_PURPOSE,
            policyVersion = termsVersion,
        )

    private companion object {
        private const val TERMS_ACCEPTANCE_PURPOSE = "terms.acceptance"
    }
}
