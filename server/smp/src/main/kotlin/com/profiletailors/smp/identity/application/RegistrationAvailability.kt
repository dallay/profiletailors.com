package com.profiletailors.smp.identity.application

/**
 * Output port exposing the registration-enabled policy to the application layer.
 *
 * Implementations live in the infrastructure layer and read the effective policy from the
 * runtime configuration. Returning `false` must reject every [RegisterUserCommand] dispatch
 * with [RegistrationDisabledException], regardless of the entry point (HTTP, internal call,
 * future channel).
 */
fun interface RegistrationAvailability {
    fun isRegistrationEnabled(): Boolean
}
