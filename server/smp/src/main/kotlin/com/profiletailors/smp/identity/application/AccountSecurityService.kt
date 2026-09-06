package com.profiletailors.smp.identity.application

import com.profiletailors.smp.credentials.application.RefreshSessionLifecycleService
import org.springframework.stereotype.Service

class InvalidCurrentPasswordException : RuntimeException("Incorrect current password.")
class ChangePasswordValidationException(message: String) : RuntimeException(message)
class LocalPasswordCredentialNotFoundException : RuntimeException("Account does not have a local password credential.")

data class ChangePasswordCommand(
    val principalId: String,
    val currentPassword: String,
    val newPassword: String,
    val rawRefreshToken: String? = null,
)

data class SignInMethodDto(
    val provider: String,
    val type: String,
    val status: String,
    val identifier: String? = null,
)

data class SecurityCapabilitiesDto(
    val hasLocalPassword: Boolean,
    val signInMethods: List<SignInMethodDto>,
)

@Service
class AccountSecurityService(
    private val localPasswordCredentialGateway: LocalPasswordCredentialGateway,
    private val passwordHasher: PasswordHasher,
    private val principalIdentityLookup: PrincipalIdentityLookup,
    private val refreshSessionLifecycleService: RefreshSessionLifecycleService,
) {
    suspend fun getSecurityCapabilities(principalId: String): SecurityCapabilitiesDto {
        val credential = localPasswordCredentialGateway.findByPrincipalId(principalId)
        val identityFacts = principalIdentityLookup.findByPrincipalId(principalId)
        val hasLocalPassword = credential != null

        val signInMethods = mutableListOf<SignInMethodDto>()
        if (credential != null) {
            signInMethods.add(
                SignInMethodDto(
                    provider = "password",
                    type = "password",
                    status = "ACTIVE",
                    identifier = credential.email,
                ),
            )
        }

        if (identityFacts?.provider != null && identityFacts.provider != "local") {
            signInMethods.add(
                SignInMethodDto(
                    provider = identityFacts.provider,
                    type = "oauth",
                    status = "CONNECTED",
                    identifier = identityFacts.email,
                ),
            )
        }

        return SecurityCapabilitiesDto(
            hasLocalPassword = hasLocalPassword,
            signInMethods = signInMethods,
        )
    }

    suspend fun changePassword(command: ChangePasswordCommand) {
        val credential = localPasswordCredentialGateway.findByPrincipalId(command.principalId)
            ?: throw LocalPasswordCredentialNotFoundException()

        if (!passwordHasher.matches(command.currentPassword, credential.passwordHash)) {
            throw InvalidCurrentPasswordException()
        }

        if (command.newPassword.length < 12) {
            throw ChangePasswordValidationException("Password must contain at least 12 characters.")
        }

        val newPasswordHash = passwordHasher.hash(command.newPassword)
        localPasswordCredentialGateway.updatePasswordHash(command.principalId, newPasswordHash)

        refreshSessionLifecycleService.revokeOthersForPrincipal(command.principalId, command.rawRefreshToken)
    }
}
