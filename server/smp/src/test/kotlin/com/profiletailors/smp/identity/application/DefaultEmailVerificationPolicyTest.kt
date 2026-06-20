package com.profiletailors.smp.identity.application

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class DefaultEmailVerificationPolicyTest {

    private val policy = DefaultEmailVerificationPolicy()

    @ParameterizedTest
    @EnumSource(AuthFeature::class)
    fun `requiresVerification returns true for all features`(feature: AuthFeature) {
        assertTrue(policy.requiresVerification(feature))
    }

    @Test
    fun `requiresVerification returns true for PUBLISH_CONTENT`() {
        assertTrue(policy.requiresVerification(AuthFeature.PUBLISH_CONTENT))
    }

    @Test
    fun `requiresVerification returns true for SCHEDULE_POST`() {
        assertTrue(policy.requiresVerification(AuthFeature.SCHEDULE_POST))
    }

    @Test
    fun `requiresVerification returns true for INVITE_TEAM`() {
        assertTrue(policy.requiresVerification(AuthFeature.INVITE_TEAM))
    }

    @Test
    fun `requiresVerification returns true for CONNECT_SOCIAL`() {
        assertTrue(policy.requiresVerification(AuthFeature.CONNECT_SOCIAL))
    }

    @Test
    fun `requiresVerification returns true for ACCESS_BILLING`() {
        assertTrue(policy.requiresVerification(AuthFeature.ACCESS_BILLING))
    }

    @Test
    fun `requiresVerification returns true for ENABLE_AUTOMATIONS`() {
        assertTrue(policy.requiresVerification(AuthFeature.ENABLE_AUTOMATIONS))
    }
}
