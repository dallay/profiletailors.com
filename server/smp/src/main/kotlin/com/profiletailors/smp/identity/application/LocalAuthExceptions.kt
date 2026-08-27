package com.profiletailors.smp.identity.application

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.smp.identity.domain.EmailStatus

class InvalidEmailPasswordException : RuntimeException("Invalid email or password.")

class UserAlreadyExistsException(email: String) : RuntimeException("User already exists.")

class InvalidRegistrationInputException(message: String) : RuntimeException(message)

class RegistrationValidationException(message: String) : RuntimeException(message)

class RegistrationDisabledException : RuntimeException("Registration is not available.")

class RegistrationInvitationRequiredException : RuntimeException("A valid invitation is required to register.")

class UnverifiedEmailException(val email: String) : RuntimeException("Email verification required for '$email'.")

open class InvalidVerificationTokenException(message: String = "Invalid verification token.") :
    RuntimeException(message)

class ExpiredVerificationTokenException : InvalidVerificationTokenException("Verification token has expired.")

class UsedVerificationTokenException : InvalidVerificationTokenException("Verification token has already been used.")

class FeatureEmailVerificationRequired(val feature: AuthFeature) :
    RuntimeException("Email verification required for feature: ${feature.name}.")

/**
 * Enforces email verification gating for [feature].
 *
 * @throws FeatureEmailVerificationRequired when [policy] requires verification for [feature]
 *         and [principal]'s email status is not VERIFIED.
 */
suspend fun requireEmailVerification(
    principal: PrincipalContext,
    principalIdentityLookup: PrincipalIdentityLookup,
    policy: EmailVerificationPolicy,
    feature: AuthFeature,
) {
    if (!policy(feature)) return
    val emailStatus = principalIdentityLookup.findByPrincipalId(principal.principalId)?.emailStatus
    if (emailStatus != EmailStatus.VERIFIED) {
        throw FeatureEmailVerificationRequired(feature)
    }
}

/**
 * No-op [PrincipalContextProvider] that always returns null principal.
 * Use as default in handlers where email-verification gating is not yet wired,
 * to keep existing test constructors working without changes.
 */
fun noOpPrincipalContextProvider(): PrincipalContextProvider = NoOpPrincipalContextProviderImpl()

private class NoOpPrincipalContextProviderImpl : PrincipalContextProvider {
    override suspend fun current(): PrincipalContext? = null
}

/**
 * Permissive [PrincipalContextProvider] that returns a dummy principal.
 * Use as default in tests that call handler.handle() directly and the gate
 * is disabled via PermissiveEmailVerificationPolicy — this prevents
 * MissingPrincipalContextException while keeping the handler logic intact.
 */
fun permissivePrincipalContextProvider(): PrincipalContextProvider = PermissivePrincipalContextProviderImpl()

private class PermissivePrincipalContextProviderImpl : PrincipalContextProvider {
    // Dummy principal — safe because PermissiveEmailVerificationPolicy always returns false,
    // so requireEmailVerification exits before any principal attribute is used.
    private val dummy = PrincipalContext(
        principalId = "test-principal",
        principalType = PrincipalType.USER,
        subject = "local:test@example.com",
        provider = null,
        displayIdentity = "Test User",
        authenticationMethod = "TEST",
    )
    override suspend fun current(): PrincipalContext = dummy
}
