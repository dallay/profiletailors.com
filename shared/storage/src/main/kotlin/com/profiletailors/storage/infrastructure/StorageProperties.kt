package com.profiletailors.storage.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "platform.storage")
class StorageProperties(
    var default: String = "local",
    var providers: Map<String, Map<String, String>> = emptyMap()
)
