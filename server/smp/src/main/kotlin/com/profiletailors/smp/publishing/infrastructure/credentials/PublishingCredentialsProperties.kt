package com.profiletailors.smp.publishing.infrastructure.credentials

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "publishing.credentials.encryption")
class PublishingCredentialsProperties {
    var encryptionKey: String? = null

    init {
        if (encryptionKey.isNullOrBlank()) {
            throw IllegalStateException(
                "publishing.credentials.encryption.key must be set. " +
                    "Set PUBLISHING_CREDENTIALS_KEY environment variable with a 32-byte Base64-encoded key.",
            )
        }
    }
}
