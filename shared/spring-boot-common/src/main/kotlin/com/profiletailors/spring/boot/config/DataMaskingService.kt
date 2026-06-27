package com.profiletailors.spring.boot.config

import org.springframework.stereotype.Service

@Service
class DataMaskingService(private val hasherRegistry: HasherRegistry) {
    fun hashData(data: String, hasherName: String? = null): String {
        val hasher = hasherRegistry.get(hasherName)
        return hasher.hash(data)
    }
}
