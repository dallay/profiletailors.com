package com.profiletailors.smp.platform.infrastructure.http

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.accept.ApiVersionResolver
import org.springframework.web.server.ServerWebExchange

/**
 * Configuration for Spring WebFlux features including API Versioning.
 */
@Configuration
class WebFluxConfiguration {

    @Bean
    fun mediaTypeVersionResolver(): ApiVersionResolver = MediaTypeVersionResolver()

    /**
     * Resolver that extracts the version from the custom vendor media type
     * in the Accept header (e.g., application/vnd.api.v1+json).
     */
    class MediaTypeVersionResolver : ApiVersionResolver {
        private val versionRegex = Regex("^vnd\\.api\\.v(\\d+)\\+json$")

        override fun resolveVersion(exchange: ServerWebExchange): String? {
            val acceptHeaders = exchange.request.headers.accept
            for (mediaType in acceptHeaders) {
                val matchResult = versionRegex.matchEntire(mediaType.subtype)
                if (matchResult != null) {
                    return matchResult.groupValues[1]
                }
            }
            return null
        }
    }
}
