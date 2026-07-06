package com.profiletailors.smp.mediaprovider.unsplash

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "mediaprovider.unsplash")
class UnsplashProperties(
    val enabled: Boolean = false,
    val accessKey: String = "",
    val baseUrl: String = "https://api.unsplash.com",
    val timeout: Duration = DEFAULT_TIMEOUT,
    val pageSize: Int = DEFAULT_PAGE_SIZE,
) {
    init {
        require(baseUrl.isNotBlank()) { "mediaprovider.unsplash.base-url must not be blank" }
        require(timeout > Duration.ZERO) { "mediaprovider.unsplash.timeout must be greater than zero" }
        require(pageSize > 0) { "mediaprovider.unsplash.page-size must be greater than zero" }
        if (enabled) {
            require(accessKey.isNotBlank()) {
                "UNSPLASH_ACCESS_KEY must be configured when Unsplash provider is enabled"
            }
        }
    }

    override fun toString(): String =
        "UnsplashProperties(enabled=$enabled, accessKey=***, baseUrl=$baseUrl, timeout=$timeout, pageSize=$pageSize)"

    companion object {
        private val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(5)
        private const val DEFAULT_PAGE_SIZE: Int = 20
    }
}
