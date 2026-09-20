package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.profiletailors.smp.platformadmin.application.ConfigurationIdempotencyCodec
import org.springframework.stereotype.Component

@Component
class JacksonConfigurationIdempotencyCodec(private val objectMapper: ObjectMapper = jacksonObjectMapper()) :
    ConfigurationIdempotencyCodec {
    override fun encode(value: Any): String = objectMapper.writeValueAsString(value)

    override fun <T : Any> decode(responseJson: String, responseType: Class<T>): T =
        objectMapper.readValue(responseJson, responseType)
}
