package com.profiletailors.smp.identity.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.common.domain.bus.query.QueryHandler

/**
 * Public projection of the unauthenticated capabilities advertised by the platform.
 *
 * @property registrationEnabled whether [RegisterUserCommand] is accepted by the application.
 */
data class PublicCapabilities(val registrationEnabled: Boolean)

/**
 * Query requesting the current public capabilities. Routed by the [Mediator] to
 * [GetPublicCapabilitiesHandler]. The query carries no payload because the projected
 * capabilities are global server-side state.
 */
class GetPublicCapabilitiesQuery : Query<PublicCapabilities>

@Service
internal class GetPublicCapabilitiesHandler(private val registrationAvailability: RegistrationAvailabilityPort) :
    QueryHandler<GetPublicCapabilitiesQuery, PublicCapabilities> {
    override suspend fun handle(query: GetPublicCapabilitiesQuery): PublicCapabilities =
        PublicCapabilities(registrationEnabled = registrationAvailability.isRegistrationEnabled())
}
