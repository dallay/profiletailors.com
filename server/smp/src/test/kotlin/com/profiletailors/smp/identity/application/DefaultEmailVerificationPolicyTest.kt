package com.profiletailors.smp.identity.application

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DefaultEmailVerificationPolicyTest {

    private val policy = emailVerificationPolicyOf()

    @ParameterizedTest
    @ValueSource(strings = [
        "PUBLISH_CONTENT",
        "SCHEDULE_POST",
        "INVITE_TEAM",
        "CONNECT_SOCIAL",
        "ACCESS_BILLING",
        "ENABLE_AUTOMATIONS",
    ])
    fun `requiresVerification returns true for restricted feature`(featureName: String) {
        val feature = AuthFeature.valueOf(featureName)
        assertTrue(policy(feature))
    }
}
