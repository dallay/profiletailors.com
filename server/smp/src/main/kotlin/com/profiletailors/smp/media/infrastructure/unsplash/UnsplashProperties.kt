package com.profiletailors.smp.media.infrastructure.unsplash

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** Typed configuration for the server-side Unsplash API adapter. */
@ConfigurationProperties(prefix = "mediaprovider.unsplash")
data class UnsplashProperties(
    val enabled: Boolean = false,
    val accessKey: String = "",
    val baseUrl: String = "https://api.unsplash.com",
    val timeout: Duration = Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS),
    val pageSize: Int = DEFAULT_PAGE_SIZE,
    val maxImportBytes: Long = DEFAULT_MAX_IMPORT_BYTES,
) {
    /** Whether the adapter has all mandatory production configuration. */
    val isConfigured: Boolean
        get() = enabled && accessKey.isNotBlank()

    private companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 5L
        const val DEFAULT_PAGE_SIZE = 20
        const val DEFAULT_MAX_IMPORT_BYTES = 26_214_400L
    }
}
