package com.profiletailors.smp.platformadmin.application.contracts

import java.util.UUID

fun interface BootstrapTermsConsentVerifier {
    suspend fun hasCurrentTermsConsent(principalId: UUID, termsVersion: String): Boolean
}
