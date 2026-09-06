package com.profiletailors.smp.privacy.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.profiletailors.common.domain.Service
import com.profiletailors.smp.privacy.application.PrivacyDataSerializer

@Service
class JacksonPrivacyDataSerializer(private val mapper: ObjectMapper = jacksonObjectMapper()) : PrivacyDataSerializer {
    override fun toJson(data: Any?): String = mapper.writeValueAsString(data)
}
