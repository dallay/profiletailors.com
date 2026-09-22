package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.profiletailors.smp.platformadmin.application.ConfigurationIdempotencyCodec
import org.springframework.stereotype.Component

@Component
class JacksonConfigurationIdempotencyCodec : ConfigurationIdempotencyCodec {
    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    override fun encode(value: Any): String = objectMapper.writeValueAsString(value)

    override fun <T : Any> decode(responseJson: String, responseType: Class<T>): T =
        objectMapper.readValue(responseJson, responseType)
}
