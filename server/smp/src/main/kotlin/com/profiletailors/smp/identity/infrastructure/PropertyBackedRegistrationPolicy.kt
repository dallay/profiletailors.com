package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.common.domain.Service
import com.profiletailors.smp.identity.application.RegistrationPolicy
import com.profiletailors.smp.identity.domain.RegistrationDecision

@Service
internal class PropertyBackedRegistrationPolicy(private val properties: RegistrationConfigurationProperties) :
    RegistrationPolicy {
    /**
         * Evaluates the registration policy for the supplied invitation token state.
         *
         * @param hasInvitationToken Whether an invitation token is present.
         * @return The resulting registration decision.
         */
        override fun evaluate(hasInvitationToken: Boolean): RegistrationDecision =
        properties.mode.evaluate(hasInvitationToken)
}
