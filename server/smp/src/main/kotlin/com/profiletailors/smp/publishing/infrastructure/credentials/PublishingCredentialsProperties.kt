package com.profiletailors.smp.publishing.infrastructure.credentials

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "publishing.credentials.encryption")
class PublishingCredentialsProperties {
    var encryptionKey: String? = null
}
