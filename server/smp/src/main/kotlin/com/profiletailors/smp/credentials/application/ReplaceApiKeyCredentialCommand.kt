package com.profiletailors.smp.credentials.application

import com.profiletailors.smp.platform.application.Command
import com.profiletailors.smp.platform.application.CommandHandler
import org.springframework.stereotype.Component
import java.security.SecureRandom

private const val API_KEY_PREFIX = "ptk_"
private const val LOOKUP_KEY_BYTES = 12
private const val SECRET_BYTES = 24
private const val CREDENTIAL_REFERENCE_BYTES = 16

data class ReplaceApiKeyCredentialCommand(
    val predecessorCredentialReference: String,
) : Command<ReplaceApiKeyCredentialResult>

data class ReplaceApiKeyCredentialResult(
    val predecessorCredentialReference: String,
    val successorCredentialReference: String,
    val successorPlaintextApiKey: String,
)

interface ApiKeyCredentialReplacementGateway {
    suspend fun replaceActiveCredential(command: ReplaceApiKeyCredentialCommand): ReplaceApiKeyCredentialResult
}

@Component
class ReplaceApiKeyCredentialHandler(
    private val gateway: ApiKeyCredentialReplacementGateway,
) : CommandHandler<ReplaceApiKeyCredentialCommand, ReplaceApiKeyCredentialResult> {
    override suspend fun handle(command: ReplaceApiKeyCredentialCommand): ReplaceApiKeyCredentialResult =
        gateway.replaceActiveCredential(command)
}

interface ApiKeyCredentialValueFactory {
    fun nextCredentialReference(): String

    fun nextPlaintextApiKey(): PlaintextApiKey

    data class PlaintextApiKey(
        val lookupKey: String,
        val keyPrefix: String,
        val secret: String,
    ) {
        val value: String = "$lookupKey.$secret"
    }
}

@Component
class SecureRandomApiKeyCredentialValueFactory : ApiKeyCredentialValueFactory {
    private val secureRandom = SecureRandom()

    override fun nextCredentialReference(): String = "api-key-${secureToken(CREDENTIAL_REFERENCE_BYTES)}"

    override fun nextPlaintextApiKey(): ApiKeyCredentialValueFactory.PlaintextApiKey {
        val lookupKey = "$API_KEY_PREFIX${secureToken(LOOKUP_KEY_BYTES)}"
        val secret = secureToken(SECRET_BYTES)
        return ApiKeyCredentialValueFactory.PlaintextApiKey(
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

