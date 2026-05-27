package com.profiletailors.smp.publishing.infrastructure.credentials

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "publishing.credentials.encryption")
class PublishingCredentialsProperties {
    var encryptionKey: String? = "dGVzdC1lbmNyeXB0aW9uLWtleS0xMjM0NTY3ODkwMTI=" // Default test key
}
