package com.profiletailors.smp.credentials.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.smp.credentials.application.ApiKeyCredentialReplacementGateway
import com.profiletailors.smp.credentials.application.ReplaceApiKeyCredentialCommand
import com.profiletailors.smp.credentials.application.ReplaceApiKeyCredentialResult
import java.security.SecureRandom

private const val API_KEY_PREFIX = "ptk_"
private const val LOOKUP_KEY_BYTES = 12
private const val SECRET_BYTES = 24
private const val CREDENTIAL_REFERENCE_BYTES = 16

@Service
class ReplaceApiKeyCredentialHandler(private val gateway: ApiKeyCredentialReplacementGateway) :
    CommandWithResultHandler<ReplaceApiKeyCredentialCommand, ReplaceApiKeyCredentialResult> {
    override suspend fun handle(command: ReplaceApiKeyCredentialCommand): ReplaceApiKeyCredentialResult =
        gateway.replaceActiveCredential(command)
}

@Service
internal class SecureRandomApiKeyCredentialValueFactory :
    com.profiletailors.smp.credentials.application.ApiKeyCredentialValueFactory {
    private val secureRandom = SecureRandom()

    override fun nextCredentialReference(): String = "api-key-${secureToken(CREDENTIAL_REFERENCE_BYTES)}"

    override fun nextPlaintextApiKey():
        com.profiletailors.smp.credentials.application.ApiKeyCredentialValueFactory.PlaintextApiKey {
        val lookupKey = "${API_KEY_PREFIX}${secureToken(LOOKUP_KEY_BYTES)}"
        val secret = secureToken(SECRET_BYTES)
        return com.profiletailors.smp.credentials.application.ApiKeyCredentialValueFactory.PlaintextApiKey(
            lookupKey = lookupKey,
            keyPrefix = lookupKey,
            secret = secret,
        )
    }

    private fun secureToken(size: Int): String {
        val bytes = ByteArray(size)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
