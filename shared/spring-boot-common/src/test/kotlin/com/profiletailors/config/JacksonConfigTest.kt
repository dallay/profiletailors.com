package com.profiletailors.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

class JacksonConfigTest {

    private val config = JacksonConfig()

    @Test
    fun `should create a JsonMapper instance`() {
        val mapper = config.jsonMapper()
        assertNotNull(mapper)
    }

    @Test
    fun `should disable writing dates as timestamps`() {
        val mapper = config.jsonMapper()
        assertTrue(mapper.isEnabled(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).not())
    }

    @Test
    fun `should not fail on unknown properties`() {
        val mapper = config.jsonMapper()
        assertTrue(mapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).not())
    }

    @Test
    fun `should register KotlinModule`() {
        val mapper = config.jsonMapper()

        val hasKotlinModule = mapper.registeredModules().any { it is KotlinModule }
        assertTrue(hasKotlinModule)
    }

}
