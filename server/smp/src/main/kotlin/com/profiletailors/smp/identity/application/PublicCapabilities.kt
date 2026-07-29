package com.profiletailors.smp.identity.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.common.domain.bus.query.QueryHandler

/**
 * Represents a configured Single Sign-On provider available to users.
 *
 * @property id unique identifier for the SSO provider (e.g., "google", "github").
 * @property displayName human-readable name shown in the UI (e.g., "Google", "GitHub").
 */
data class PublicSsoProvider(val id: String, val displayName: String)

/**
 * Public projection of the unauthenticated capabilities advertised by the platform.
 *
 * @property registrationEnabled whether [RegisterUserCommand] is accepted by the application.
 * @property passwordRecoveryEnabled whether password recovery flows are operational.
 * @property ssoProviders list of available SSO providers for authentication.
 */
data class PublicCapabilities(
    val registrationEnabled: Boolean,
    val passwordRecoveryEnabled: Boolean,
    val ssoProviders: List<PublicSsoProvider> = emptyList(),
)

/**
 * Query requesting the current public capabilities. Routed by the [Mediator] to
 * [GetPublicCapabilitiesHandler]. The query carries no payload because the projected
 * capabilities are global server-side state.
 */
class GetPublicCapabilitiesQuery : Query<PublicCapabilities>

@Service
internal class GetPublicCapabilitiesHandler(
    private val registrationAvailability: RegistrationAvailabilityPort,
    private val passwordRecoveryAvailability: PasswordRecoveryAvailabilityPort,
) : QueryHandler<GetPublicCapabilitiesQuery, PublicCapabilities> {
    override suspend fun handle(query: GetPublicCapabilitiesQuery): PublicCapabilities = PublicCapabilities(
        registrationEnabled = registrationAvailability.isRegistrationEnabled(),
        passwordRecoveryEnabled = passwordRecoveryAvailability.isPasswordRecoveryEnabled(),
        ssoProviders = emptyList(), // No SSO in this release
    )
}
