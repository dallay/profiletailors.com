package com.profiletailors.smp.identity.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.common.domain.bus.query.QueryHandler
import com.profiletailors.smp.identity.domain.RegistrationDecision

/**
 * Public projection of the unauthenticated capabilities advertised by the platform.
 *
 * @property registrationEnabled whether [RegisterUserCommand] is accepted by the application.
 * @property passwordRecoveryEnabled whether password recovery flows are operational.
 */
data class PublicCapabilities(
    val registrationEnabled: Boolean,
    val passwordRecoveryEnabled: Boolean,
    val invitationAcceptanceEnabled: Boolean = true,
)

/**
 * Query requesting the current public capabilities. Routed by the [Mediator] to
 * [GetPublicCapabilitiesHandler]. The query carries no payload because the projected
 * capabilities are global server-side state.
 */
class GetPublicCapabilitiesQuery : Query<PublicCapabilities>

@Service
internal class GetPublicCapabilitiesHandler(
    private val registrationPolicy: RegistrationPolicy,
    private val passwordRecoveryEnabled: () -> Boolean,
) : QueryHandler<GetPublicCapabilitiesQuery, PublicCapabilities> {
    override suspend fun handle(query: GetPublicCapabilitiesQuery): PublicCapabilities = PublicCapabilities(
        registrationEnabled = registrationPolicy.evaluate(hasValidInvitation = false) == RegistrationDecision.ALLOWED,
        passwordRecoveryEnabled = passwordRecoveryEnabled(),
    )
}
