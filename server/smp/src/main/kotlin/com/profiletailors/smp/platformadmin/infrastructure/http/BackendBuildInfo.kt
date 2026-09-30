package com.profiletailors.smp.platformadmin.infrastructure.http

import org.springframework.boot.info.BuildProperties
import org.springframework.stereotype.Component

@Component
class BackendBuildInfo(properties: BuildProperties) {
    val version: String = properties.version ?: "unknown"
    val revision: String = properties.get("revision") ?: "unknown"
    val builtAt: String = properties.get("builtAt") ?: "unknown"
}
