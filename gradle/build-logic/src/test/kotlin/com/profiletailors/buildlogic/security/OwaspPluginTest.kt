package com.profiletailors.buildlogic.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OwaspPluginTest {
    @Test
    fun `missing NVD API key is treated as optional`() {
        assertNull(nvdApiKey(emptyMap()))
    }

    @Test
    fun `blank NVD API key is treated as missing`() {
        assertNull(nvdApiKey(mapOf("NVD_API_KEY" to " ")))
    }

    @Test
    fun `configured NVD API key is returned`() {
        assertEquals("configured-key", nvdApiKey(mapOf("NVD_API_KEY" to "configured-key")))
    }
}
