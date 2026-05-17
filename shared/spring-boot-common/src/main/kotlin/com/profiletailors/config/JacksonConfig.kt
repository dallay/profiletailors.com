package com.profiletailors.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.ProblemDetail
import org.springframework.http.codec.CodecConfigurer
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.http.codec.json.JacksonJsonDecoder
import org.springframework.http.codec.json.JacksonJsonEncoder
import org.springframework.http.converter.json.ProblemDetailJacksonMixin
import org.springframework.web.reactive.config.WebFluxConfigurer
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jsonMapper
import tools.jackson.module.kotlin.kotlinModule

/**
 * Jackson 3 configuration for WebFlux.
 *
 * IMPORTANT: When @EnableWebFlux is used, Spring Boot's auto-configuration for Jackson
 * (spring.jackson.* properties in application.yml) is NOT automatically applied.
 * This class explicitly configures Jackson for WebFlux to ensure consistent
 * date serialization as ISO-8601 strings instead of arrays.
 *
 * Jackson 3 Changes from Jackson 2:
 * - Package changed from com.fasterxml.jackson to tools.jackson
 * - ObjectMapper replaced by JsonMapper (immutable builder pattern)
 * - Java 8 date/time support built-in (no separate JavaTimeModule needed)
 * - KotlinModule auto-registered via jsonMapper() builder
 * - WRITE_DATES_AS_TIMESTAMPS moved to DateTimeFeature (defaults to false in Jackson 3!)
 *
 * Without this configuration, LocalDate would serialize as [2018, 9, 5] instead of "2018-09-05"
 * causing frontend deserialization issues.
 */
@Configuration
class JacksonConfig : WebFluxConfigurer {

    // Lazy-initialized JsonMapper to avoid duplicate instantiation
    // in configureHttpMessageCodecs
    private val mapper: JsonMapper by lazy { jsonMapper() }

    /**
     * Primary JsonMapper bean used throughout the application.
     * Configured to serialize dates as ISO-8601 strings.
     *
     * Jackson 3 uses immutable builders - configuration is done at build time.
     * Note: WRITE_DATES_AS_TIMESTAMPS defaults to false in Jackson 3,
     * so dates are already ISO-8601 strings by default.
     */
    @Bean
    @Primary
    fun jsonMapper(): JsonMapper {
        return jsonMapper {
            // Add Kotlin module for proper Kotlin class support
            addModule(kotlinModule())

            // Jackson 3: WRITE_DATES_AS_TIMESTAMPS defaults to false, but we explicitly disable
            // to be clear about our intent. Ensures LocalDate -> "2018-09-05" not [2018, 9, 5]
            disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)

            // Don't fail on unknown properties during deserialization
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

            // CRITICAL: Register Spring's ProblemDetail mixin for proper error response serialization.
            // This mixin uses @JsonAnyGetter/@JsonAnySetter to serialize custom properties
            // (errorCategory, timestamp, traceId, etc.) as top-level JSON fields.
            // Without this, ProblemDetail.setProperty() values would not appear in the response.
            addMixIn(ProblemDetail::class.java, ProblemDetailJacksonMixin::class.java)
        }
    }

    /**
     * Configure WebFlux HTTP message codecs to use our customized JsonMapper.
     * This ensures all HTTP request/response serialization uses the correct date format.
     *
     * Jackson 3 uses JacksonJsonEncoder/JacksonJsonDecoder instead of Jackson2JsonEncoder/Decoder.
     */
    override fun configureHttpMessageCodecs(configurer: ServerCodecConfigurer) {
        val defaults: CodecConfigurer.DefaultCodecs = configurer.defaultCodecs()
        defaults.jacksonJsonDecoder(JacksonJsonDecoder(mapper))
        defaults.jacksonJsonEncoder(JacksonJsonEncoder(mapper))
    }
}
