package com.profiletailors.smp.platform.infrastructure.http

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.accept.ApiVersionResolver
import org.springframework.web.reactive.config.ApiVersionConfigurer
import org.springframework.web.reactive.config.WebFluxConfigurer
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ServerWebExchange
import reactor.netty.http.client.HttpClient
import java.time.Duration

/**
 * Configuration for Spring WebFlux features including API Versioning.
 */
@Configuration
class WebFluxConfiguration : WebFluxConfigurer {

    @Bean
    fun webClient(): WebClient {
        val httpClient = HttpClient.create()
            .responseTimeout(Duration.ofSeconds(IMAGE_PROXY_TIMEOUT_SECONDS))

        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .codecs { configurer ->
                configurer.defaultCodecs().maxInMemorySize(IMAGE_PROXY_MAX_BYTES)
            }
            .build()
    }

    @Bean
    fun mediaTypeVersionResolver(): ApiVersionResolver = MediaTypeVersionResolver()

    /**
     * Configures a default API version so requests without an explicit
     * `Accept: application/vnd.api.vN+json` header (e.g. <img> tags loading
     * the image proxy) still match. Production clients should still send
     * the vendor media type, but this keeps the API forgiving.
     */
    override fun configureApiVersioning(configurer: ApiVersionConfigurer) {
        configurer.setDefaultVersion(DEFAULT_API_VERSION)
    }

    companion object {
        private const val DEFAULT_API_VERSION = "1"
        private const val IMAGE_PROXY_MAX_BYTES = 2 * 1024 * 1024
        private const val IMAGE_PROXY_TIMEOUT_SECONDS = 10L
    }

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
