package com.profiletailors.smp.mcp.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.profiletailors.common.domain.Service
import com.profiletailors.smp.mcp.application.McpJsonSerializer

@Service
class JacksonMcpJsonSerializer(private val objectMapper: ObjectMapper = defaultObjectMapper()) : McpJsonSerializer {

    override fun <T> toJson(data: T): String = objectMapper.writeValueAsString(data)

    override fun <T> fromJson(json: String, type: Class<T>): T = objectMapper.readValue(json, type)

    companion object {
        fun defaultObjectMapper(): ObjectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }
}
