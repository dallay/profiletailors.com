package com.profiletailors.smp.shortlinks.domain

import com.profiletailors.common.domain.ValueObject
import java.net.URI

private val ALLOWED_SCHEMES = setOf("http", "https")
private val BLOCKED_SCHEMES = setOf("javascript", "data", "file", "ftp", "mailto", "chrome", "blob")

@JvmInline
@ValueObject
value class DestinationUrl(val value: String) {
    init {
        require(value.isNotBlank()) { "Destination URL must not be blank" }
        val uri = runCatching { URI.create(value) }.getOrElse {
            throw IllegalArgumentException("Malformed URL: $value")
        }
        val scheme = uri.scheme?.lowercase()
        require(scheme != null) { "Destination URL must have a scheme" }
        require(scheme !in BLOCKED_SCHEMES) { "Scheme '$scheme' is not allowed" }
        require(scheme in ALLOWED_SCHEMES) { "Only http and https schemes are allowed, got '$scheme'" }
        require(uri.host != null) { "Destination URL must have a host" }
    }
}
