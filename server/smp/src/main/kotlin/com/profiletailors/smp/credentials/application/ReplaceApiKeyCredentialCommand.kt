package com.profiletailors.smp.credentials.application

import com.profiletailors.common.domain.bus.command.CommandWithResult

data class ReplaceApiKeyCredentialCommand(val predecessorCredentialReference: String) :
    CommandWithResult<ReplaceApiKeyCredentialResult>

data class ReplaceApiKeyCredentialResult(
    val predecessorCredentialReference: String,
    val successorCredentialReference: String,
    val successorPlaintextApiKey: String,
)

fun interface ApiKeyCredentialReplacementGateway {
    suspend fun replaceActiveCredential(command: ReplaceApiKeyCredentialCommand): ReplaceApiKeyCredentialResult
}

interface ApiKeyCredentialValueFactory {
    fun nextCredentialReference(): String

    fun nextPlaintextApiKey(): PlaintextApiKey

    data class PlaintextApiKey(val lookupKey: String, val keyPrefix: String, val secret: String) {
        val value: String = "$lookupKey.$secret"
    }
}
